package com.medfold.document;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.medfold.auth.CurrentUserId;
import com.medfold.document.dto.DocumentResponse;

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
}
