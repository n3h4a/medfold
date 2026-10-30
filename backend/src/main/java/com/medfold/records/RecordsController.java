package com.medfold.records;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medfold.auth.CurrentUserId;
import com.medfold.records.dto.DocumentResultsResponse;
import com.medfold.records.dto.LabTestSummary;
import com.medfold.records.dto.LabTrendResponse;
import com.medfold.records.dto.MedicationResponse;

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

    /** Tests the user has results for, with the latest value of each. */
    @GetMapping("/labs")
    public List<LabTestSummary> labTests(@CurrentUserId UUID userId) {
        return recordsService.labTests(userId);
    }

    /** One test across all of the user's reports, for trends. */
    @GetMapping("/labs/{testKey}")
    public LabTrendResponse labTrend(@CurrentUserId UUID userId, @PathVariable String testKey) {
        return recordsService.labTrend(userId, testKey);
    }

    @GetMapping("/medications")
    public List<MedicationResponse> medications(@CurrentUserId UUID userId) {
        return recordsService.medications(userId);
    }
}
