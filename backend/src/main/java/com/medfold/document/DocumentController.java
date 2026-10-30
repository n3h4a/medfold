package com.medfold.document;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.medfold.auth.CurrentUserId;
import com.medfold.common.PageResponse;
import com.medfold.document.dto.DocumentResponse;
import com.medfold.document.dto.DocumentStatusResponse;
import com.medfold.document.dto.DocumentUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** Uploads one file. The client sends several files as parallel requests. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@CurrentUserId UUID userId, @RequestPart("file") MultipartFile file) {
        DocumentResponse created = documentService.upload(userId, file);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(@CurrentUserId UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documentService.list(userId, page, size);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return documentService.get(userId, id);
    }

    /** Corrects extracted details; fields left out are unchanged. */
    @PatchMapping("/{id}")
    public DocumentResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
            @Valid @RequestBody DocumentUpdateRequest request) {
        return documentService.update(userId, id, request);
    }

    @GetMapping("/{id}/status")
    public DocumentStatusResponse status(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return documentService.status(userId, id);
    }

    /** Reads and extracts the document again. 202: the work happens in the background; poll /status. */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentStatusResponse> reprocess(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return ResponseEntity.accepted().body(documentService.reprocess(userId, id));
    }

    /**
     * Streams the original file for viewing in the browser. Medical files must not be cached by
     * shared caches or kept on disk by the browser.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@CurrentUserId UUID userId, @PathVariable UUID id) {
        DocumentService.DocumentFile file = documentService.file(userId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        documentService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
