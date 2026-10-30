package com.medfold.processing;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Audit record of one successful AI run: which OCR engine, model and prompt version produced a
 * document's data, whether the safety filter changed anything, and the exact raw output.
 */
@Entity
@Table(name = "extraction_runs")
public class ExtractionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "ocr_provider", length = 20)
    private String ocrProvider;

    @Column(name = "llm_provider", nullable = false, length = 50)
    private String llmProvider;

    @Column(name = "llm_model", nullable = false, length = 100)
    private String llmModel;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "safety_filtered", nullable = false)
    private boolean safetyFiltered;

    @Column(name = "safety_issues", columnDefinition = "text")
    private String safetyIssues;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "raw_output", nullable = false, columnDefinition = "text")
    private String rawOutput;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExtractionRun() {
    }

    public ExtractionRun(UUID documentId, UUID userId, String ocrProvider, String llmProvider, String llmModel,
            String promptVersion, int attempts, boolean safetyFiltered, String safetyIssues, Integer inputTokens,
            Integer outputTokens, Integer durationMs, String rawOutput) {
        this.documentId = documentId;
        this.userId = userId;
        this.ocrProvider = ocrProvider;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.promptVersion = promptVersion;
        this.attempts = attempts;
        this.safetyFiltered = safetyFiltered;
        this.safetyIssues = safetyIssues;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.durationMs = durationMs;
        this.rawOutput = rawOutput;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getOcrProvider() {
        return ocrProvider;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isSafetyFiltered() {
        return safetyFiltered;
    }

    public String getSafetyIssues() {
        return safetyIssues;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
