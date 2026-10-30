package com.medfold.records.dto;

import java.util.List;

/** Every result of one test across the user's reports, oldest first (undated results last). */
public record LabTrendResponse(String testKey, String testName, List<LabResultResponse> results) {
}
