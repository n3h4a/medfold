package com.medfold.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.medfold.common.ApiException;
import com.medfold.common.PageResponse;
import com.medfold.document.dto.DocumentResponse;
import com.medfold.document.dto.DocumentStatusResponse;
import com.medfold.storage.StorageService;
import com.medfold.storage.StoredFile;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final long maxFileBytes;

    public DocumentService(DocumentRepository documentRepository, StorageService storageService,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.maxFileBytes = maxFileSize.toBytes();
    }

    /**
     * Validates and stores one uploaded file, then records it as UPLOADED.
     * The file is written before the database row; if the row can't be saved the file is removed again.
     */
    public DocumentResponse upload(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Choose a file to upload");
        }
        if (file.getSize() > maxFileBytes) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "File is larger than the 10 MB limit");
        }

        DetectedFileType type = detectType(file);
        StoredFile stored = store(userId, file, type);

        Optional<Document> existing = documentRepository.findByUserIdAndSha256(userId, stored.sha256());
        if (existing.isPresent()) {
            storageService.delete(stored.key());
            throw duplicate(existing.get().getId());
        }

        Document document = new Document(userId, cleanFilename(file.getOriginalFilename(), type), stored.key(),
                type.mimeType(), stored.sizeBytes(), stored.sha256());
        try {
            return DocumentResponse.from(documentRepository.saveAndFlush(document));
        } catch (DataIntegrityViolationException ex) {
            // The same file was uploaded concurrently and the other request won.
            storageService.delete(stored.key());
            UUID winner = documentRepository.findByUserIdAndSha256(userId, stored.sha256())
                    .map(Document::getId).orElse(null);
            throw duplicate(winner);
        } catch (RuntimeException ex) {
            storageService.delete(stored.key());
            throw ex;
        }
    }

    /** The user's documents in timeline order. Page size is capped so one request can't load everything. */
    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(documentRepository.findTimeline(userId, pageRequest), DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID userId, UUID documentId) {
        return DocumentResponse.from(findOwned(userId, documentId));
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse status(UUID userId, UUID documentId) {
        return DocumentStatusResponse.from(findOwned(userId, documentId));
    }

    public record DocumentFile(Resource resource, String mimeType, String filename) {
    }

    @Transactional(readOnly = true)
    public DocumentFile file(UUID userId, UUID documentId) {
        Document document = findOwned(userId, documentId);
        return new DocumentFile(storageService.load(document.getStorageKey()), document.getMimeType(),
                document.getOriginalFilename());
    }

    /**
     * Deletes the row, and the file only once that delete has committed. If removing the file
     * then fails, the document is still gone for the user and the orphan file is logged, rather
     * than leaving a row that points to a missing file.
     */
    @Transactional
    public void delete(UUID userId, UUID documentId) {
        Document document = findOwned(userId, documentId);
        documentRepository.delete(document);
        String storageKey = document.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.delete(storageKey);
                } catch (RuntimeException ex) {
                    log.error("Document {} deleted but its file could not be removed", documentId, ex);
                }
            }
        });
    }

    /** Someone else's document and a non-existent one look identical: 404 either way. */
    private Document findOwned(UUID userId, UUID documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found"));
    }

    private static DetectedFileType detectType(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return FileTypeDetector.detect(in).orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_FILE_TYPE", "Only PDF, JPG and PNG files are supported"));
        } catch (IOException ex) {
            throw unreadable(ex);
        }
    }

    private StoredFile store(UUID userId, MultipartFile file, DetectedFileType type) {
        try (InputStream in = file.getInputStream()) {
            return storageService.store(userId, in, type.extension());
        } catch (IOException ex) {
            throw unreadable(ex);
        }
    }

    /**
     * Keeps only the base name (some browsers send full client paths), strips control characters
     * and limits the length. Used for display only; the stored file name is generated.
     */
    static String cleanFilename(String original, DetectedFileType type) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "document." + type.extension();
        }
        return name.length() <= MAX_FILENAME_LENGTH ? name : name.substring(name.length() - MAX_FILENAME_LENGTH);
    }

    private static ApiException duplicate(UUID existingId) {
        Map<String, Object> details = existingId == null ? null : Map.of("existingDocumentId", existingId);
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT", "You have already uploaded this file",
                details);
    }

    private static ApiException unreadable(IOException ex) {
        log.warn("Could not read uploaded file", ex);
        return new ApiException(HttpStatus.BAD_REQUEST, "FILE_UNREADABLE", "The file could not be read. Please try again");
    }
}
