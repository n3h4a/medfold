"""The offline extractor used when no real model is configured."""

import json
import re
from pathlib import Path

from app.extraction.models import PageInput
from app.extraction.service import run_extraction
from app.llm.mock import MockLlmProvider

EXAMPLE = json.loads((Path(__file__).parent / "examples" / "cbc_synthetic.json").read_text(encoding="utf-8"))

# Text as produced by /v1/ocr (Tesseract) from the synthetic prescription photo.
PRESCRIPTION_OCR = """Dr. A. Mehta, MD

General Physician - Sunrise Clinic
Patient: Test Patient (SYNTHETIC)
R

1. Metformin 500 mg — 1-0-1 after food — 30 days
2. Vitamin D3 60,000 IU — once weekly — 8 weeks

Synthetic sample — not a real prescription.

12 Feb 2025"""

JUDGMENT_WORDS = re.compile(
    r"\b(normal|abnormal|high|low|elevated|concerning|worrying|reassuring|healthy|diagnos(?:is|es|ed|e)|should|recommend\w*|risk|advise\w*)\b",
    re.I,
)


def extract(pages):
    return run_extraction([PageInput(page_number=n, text=t) for n, t in pages], MockLlmProvider()).extraction.model_dump(mode="json")


def test_matches_the_golden_lab_report():
    result = extract(EXAMPLE["pages"])
    expected = EXAMPLE["expected"]

    for field in ("docType", "docDate", "title", "providerName", "facilityName"):
        assert result[field] == expected[field], field
    assert [{k: v for k, v in row.items() if k != "testKey"} for row in result["labResults"]] == expected["labResults"]
    assert [row["testKey"] for row in result["labResults"]] == ["haemoglobin", "wbc", "platelets", "hba1c"]
    assert result["medications"] == []


def test_reads_an_ocrd_prescription():
    result = extract([(1, PRESCRIPTION_OCR)])

    assert result["docType"] == "PRESCRIPTION"
    assert result["docDate"] == "2025-02-12"
    assert result["providerName"] == "Dr. A. Mehta"
    assert result["facilityName"] == "Sunrise Clinic"
    assert result["medications"] == [
        {"name": "Metformin", "dosage": "500 mg", "frequency": "1-0-1", "route": None, "duration": "30 days", "instructions": "after food"},
        {"name": "Vitamin D3", "dosage": "60,000 IU", "frequency": "once weekly", "route": None, "duration": "8 weeks", "instructions": None},
    ]
    assert result["labResults"] == []


def test_summaries_describe_and_never_judge():
    for pages in (EXAMPLE["pages"], [(1, PRESCRIPTION_OCR)]):
        result = extract(pages)
        summary = result["summary"]
        assert summary.startswith("This is a ")
        # Proper names (e.g. "City Diagnostics Laboratory") aren't judgments; check only the surrounding wording.
        for name in (result["facilityName"], result["providerName"]):
            summary = summary.replace(name or "", "")
        assert not JUDGMENT_WORDS.search(summary), summary


def test_recognizes_other_document_types():
    assert extract([(1, "CITY HOSPITAL\nDISCHARGE SUMMARY\nDate of discharge: 03/02/2024")])["docType"] == "DISCHARGE_SUMMARY"
    assert extract([(1, "Ultrasound abdomen\nImpression: study performed")])["docType"] == "IMAGING_REPORT"
    assert extract([(1, "Receipt for payment of consultation fees, thank you")])["docType"] == "CONSULTATION_NOTE"
    assert extract([(1, "Fitness certificate issued to the bearer")])["docType"] == "OTHER"


def test_ignores_dates_of_birth_and_reads_day_first_dates():
    result = extract([(1, "Patient DOB: 01/02/1990\nReport date: 05/03/2024\nReference range below")])

    assert result["docDate"] == "2024-03-05"


def test_summaries_use_the_right_article():
    imaging = extract([(1, "Medfold Demo Imaging Clinic\nReport date: 2025-08-11\nX-RAY CHEST PA VIEW\nLungs: clear.")])
    assert imaging["docType"] == "IMAGING_REPORT"
    assert imaging["summary"].startswith("This is an imaging report")
