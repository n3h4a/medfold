package com.medfold.records;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One test result row from a lab report, copied as printed, plus parsed numbers for comparisons and trends. */
@Entity
@Table(name = "lab_results")
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private int position;

    @Column(name = "test_name", nullable = false)
    private String testName;

    @Column(name = "test_key", nullable = false, length = 100)
    private String testKey;

    @Column(name = "value_text", nullable = false, length = 100)
    private String valueText;

    @Column(name = "value_numeric", precision = 18, scale = 6)
    private BigDecimal valueNumeric;

    @Column(length = 50)
    private String unit;

    @Column(name = "ref_text", length = 100)
    private String refText;

    @Column(name = "ref_low", precision = 18, scale = 6)
    private BigDecimal refLow;

    @Column(name = "ref_high", precision = 18, scale = 6)
    private BigDecimal refHigh;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private LabFlag flag;

    @Column(name = "observed_on")
    private LocalDate observedOn;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "user_edited", nullable = false)
    private boolean userEdited;

    protected LabResult() {
    }

    public LabResult(UUID documentId, UUID userId, int position, String testName, String testKey, String valueText,
            String unit, String refText, Integer pageNumber, LocalDate observedOn) {
        this.documentId = documentId;
        this.userId = userId;
        this.position = position;
        this.testName = testName;
        this.testKey = testKey;
        this.valueText = valueText;
        this.unit = unit;
        this.refText = refText;
        this.pageNumber = pageNumber;
        this.observedOn = observedOn;
    }

    /** Parsed numbers and the resulting flag, filled in by the flag calculator. */
    public void applyComparison(BigDecimal valueNumeric, BigDecimal refLow, BigDecimal refHigh, LabFlag flag) {
        this.valueNumeric = valueNumeric;
        this.refLow = refLow;
        this.refHigh = refHigh;
        this.flag = flag;
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

    public int getPosition() {
        return position;
    }

    public String getTestName() {
        return testName;
    }

    public String getTestKey() {
        return testKey;
    }

    public String getValueText() {
        return valueText;
    }

    public BigDecimal getValueNumeric() {
        return valueNumeric;
    }

    public String getUnit() {
        return unit;
    }

    public String getRefText() {
        return refText;
    }

    public BigDecimal getRefLow() {
        return refLow;
    }

    public BigDecimal getRefHigh() {
        return refHigh;
    }

    public LabFlag getFlag() {
        return flag;
    }

    public LocalDate getObservedOn() {
        return observedOn;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public boolean isUserEdited() {
        return userEdited;
    }
}
