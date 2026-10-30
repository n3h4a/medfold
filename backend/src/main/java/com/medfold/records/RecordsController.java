package com.medfold.records;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medfold.auth.CurrentUserId;
import com.medfold.records.dto.DocumentResultsResponse;

@RestController
@RequestMapping("/api/v1")
public class RecordsController {

    private final RecordsService recordsService;

    public RecordsController(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    /** Lab results, medicines and explained terms extracted from one document. */
    @GetMapping("/documents/{id}/results")
    public DocumentResultsResponse documentResults(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return recordsService.forDocument(userId, id);
    }
}
