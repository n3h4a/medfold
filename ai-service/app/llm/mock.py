"""
Offline, deterministic stand-in for a language model.

It reads the same prompt a real model gets and extracts what simple text rules can find: document
type, date, doctor, facility, lab table rows and prescription lines. Results are less complete than
a real model's, but the whole pipeline works and is testable without an API key or network access.
"""

import json
import re
from datetime import date

from app.llm.base import LlmProvider, LlmRequest, LlmResponse

_PAGE = re.compile(r'<page number="(\d+)">\n(.*?)\n</page>', re.DOTALL)
_COLUMN_GAP = re.compile(r"\s{2,}")
# Names never cross a line break, so only spaces/tabs are allowed between parts.
_DOCTOR = re.compile(r"\bDr\.?[ \t]+((?:[A-Z]\.[ \t]?)*[A-Z][a-z]+(?:[ \t]+[A-Z][a-z]+)?)")
_FACILITY_WORDS = re.compile(r"\b(Laborator(?:y|ies)|Diagnostics?|Hospital|Clinic|Medical Cent(?:re|er)|Health ?care|Lab)\b", re.I)
_VALUE = re.compile(r"^(?:[<>≤≥]=?\s*)?\d[\d,]*(?:\.\d+)?$|^(?:negative|positive|nil|absent|present|reactive|non[- ]reactive|trace)$", re.I)
_RANGE = re.compile(r"\d.*[-–—].*\d|^[<>≤≥]\s*\d|^(?:up to|upto)\s*\d", re.I)
_DOSE = re.compile(r"(\d[\d,]*(?:\.\d+)?\s*(?:mg|mcg|µg|g|ml|IU|units?))\b", re.I)
_FREQUENCY = re.compile(
    r"\b(\d-\d-\d|once (?:daily|weekly|a day|a week)|twice (?:daily|a day|weekly)|thrice daily|"
    r"three times (?:a day|daily)|at night|at bedtime|OD|BD|BID|TDS|TID|QID|SOS|HS)\b", re.I)
_DURATION = re.compile(r"\b(\d+\s*(?:days?|weeks?|months?))\b", re.I)
_INSTRUCTIONS = re.compile(r"\b((?:after|before|with) (?:food|meals?|breakfast|dinner|lunch)|empty stomach)\b", re.I)
_MED_PREFIX = re.compile(r"^\s*(?:\d+[.)]\s*)?(?:(?:Tab|Cap|Syp|Inj|Tablet|Capsule)\.?\s+)?", re.I)
_MONTHS = {m: i for i, m in enumerate(["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"], 1)}
_DATE_PATTERNS = [
    (re.compile(r"\b(\d{4})-(\d{2})-(\d{2})\b"), lambda m: (int(m[1]), int(m[2]), int(m[3]))),
    (re.compile(r"\b(\d{1,2})\s+([A-Za-z]{3,9})\.?,?\s+(\d{4})\b"), lambda m: (int(m[3]), _MONTHS.get(m[2][:3].lower(), 0), int(m[1]))),
    (re.compile(r"\b([A-Za-z]{3,9})\.?\s+(\d{1,2}),?\s+(\d{4})\b"), lambda m: (int(m[3]), _MONTHS.get(m[1][:3].lower(), 0), int(m[2]))),
    # Day-first numeric dates, as used on most Indian and UK records.
    (re.compile(r"\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\b"), lambda m: (int(m[3]), int(m[2]), int(m[1]))),
]
_TERMS = {
    "CBC": "A Complete Blood Count is a common blood test that measures the different types of cells in the blood.",
    "HbA1c": "HbA1c is a blood test that reflects average blood sugar levels over roughly the past two to three months.",
    "WBC": "WBC stands for white blood cells, the cells of the immune system found in the blood.",
    "RBC": "RBC stands for red blood cells, which carry oxygen around the body.",
    "TSH": "TSH (thyroid stimulating hormone) is a hormone measured to check how the thyroid gland is working.",
    "LDL": "LDL is a type of cholesterol carried in the blood, often called 'bad' cholesterol.",
    "HDL": "HDL is a type of cholesterol carried in the blood, often called 'good' cholesterol.",
    "ESR": "ESR (erythrocyte sedimentation rate) measures how quickly red blood cells settle in a test tube.",
    "CRP": "CRP (C-reactive protein) is a protein in the blood that is measured as a general marker of inflammation.",
    "Haemoglobin": "Haemoglobin is the protein in red blood cells that carries oxygen.",
    "Platelet": "Platelets are small blood cells that help blood to clot.",
    "Creatinine": "Creatinine is a waste product in the blood that is measured to check kidney function.",
    "IU": "IU (international unit) is a standard unit used to measure the amount of some vitamins and medicines.",
}
_TYPE_LABEL = {
    "LAB_REPORT": "lab report",
    "PRESCRIPTION": "prescription",
    "DISCHARGE_SUMMARY": "discharge summary",
    "IMAGING_REPORT": "imaging report",
    "CONSULTATION_NOTE": "consultation note",
    "OTHER": "document",
}


class MockLlmProvider(LlmProvider):
    name = "mock"
    model = "medfold-rule-based-v1"

    def complete_json(self, request: LlmRequest) -> LlmResponse:
        pages = [(int(n), text) for n, text in _PAGE.findall(request.user)]
        result = extract(pages)
        return LlmResponse(text=json.dumps(result, ensure_ascii=False), model=self.model, stop_reason="end_turn")


def extract(pages: list[tuple[int, str]]) -> dict:
    full_text = "\n".join(text for _, text in pages)
    lines = [(number, line) for number, text in pages for line in text.splitlines()]
    doc_type = _doc_type(full_text)
    labs = _lab_results(lines) if doc_type in ("LAB_REPORT", "DISCHARGE_SUMMARY", "OTHER") else []
    if labs and doc_type == "OTHER":
        doc_type = "LAB_REPORT"
    meds = _medications(lines) if doc_type in ("PRESCRIPTION", "DISCHARGE_SUMMARY") else []
    doc_date = _doc_date(lines)
    provider = _doctor(full_text)
    facility = _facility(lines)
    title = _title(doc_type, lines)
    return {
        "docType": doc_type,
        "docDate": doc_date.isoformat() if doc_date else None,
        "title": title,
        "providerName": provider,
        "facilityName": facility,
        "summary": _summary(doc_type, doc_date, provider, facility, title, labs, meds),
        "termsExplained": _terms(full_text),
        "labResults": labs,
        "medications": meds,
    }


def _doc_type(text: str) -> str:
    lowered = text.lower()
    if "discharge summary" in lowered or "date of discharge" in lowered:
        return "DISCHARGE_SUMMARY"
    if re.search(r"\b(x-ray|xray|ultrasound|usg|mri|ct scan|radiolog|sonograph|mammogra)", lowered):
        return "IMAGING_REPORT"
    if "reference range" in lowered or "biological ref" in lowered or "normal range" in lowered:
        return "LAB_REPORT"
    if "℞" in text or re.search(r"\b(rx|prescription|tab\.|cap\.)", lowered) or (_DOSE.search(text) and _FREQUENCY.search(text)):
        return "PRESCRIPTION"
    if re.search(r"\b(chief complaint|consultation|history of present illness|follow[- ]up visit)", lowered):
        return "CONSULTATION_NOTE"
    return "OTHER"


def _parse_date(match_groups) -> date | None:
    try:
        year, month, day = match_groups
        parsed = date(year, month, day)
    except (ValueError, TypeError):
        return None
    return parsed if date(1900, 1, 1) <= parsed <= date.today() else None


def _doc_date(lines: list[tuple[int, str]]) -> date | None:
    """Prefers a date on a line that says what the date is (report/visit/prescription), else the first date."""
    candidates: list[tuple[int, date]] = []
    for _, line in lines:
        if re.search(r"\b(birth|dob)\b", line, re.I):
            continue
        for pattern, groups in _DATE_PATTERNS:
            for match in pattern.finditer(line):
                parsed = _parse_date(groups(match))
                if parsed:
                    priority = 0 if re.search(r"\b(report|date|visit|collected|issued)\b", line, re.I) else 1
                    candidates.append((priority, parsed))
    return min(candidates, key=lambda c: c[0])[1] if candidates else None


def _doctor(text: str) -> str | None:
    match = _DOCTOR.search(text)
    return f"Dr. {match.group(1).strip()}" if match else None


def _facility(lines: list[tuple[int, str]]) -> str | None:
    for _, line in lines:
        for segment in re.split(r"\s{3,}|\s[-–·|]\s", line):
            if _FACILITY_WORDS.search(segment) and len(segment.strip()) <= 120 and not segment.strip().lower().startswith("ref"):
                return segment.strip(" :,")
    return None


def _cells(line: str) -> list[str]:
    return [cell.strip() for cell in _COLUMN_GAP.split(line.strip()) if cell.strip()]


def _lab_results(lines: list[tuple[int, str]]) -> list[dict]:
    results = []
    for page, line in lines:
        cells = _cells(line)
        if len(cells) < 2 or not _VALUE.match(cells[1]) or not re.search(r"[A-Za-z]", cells[0]):
            continue
        rest = cells[2:]
        reference = next((c for c in rest if _RANGE.search(c)), None)
        unit = next((c for c in rest if c != reference and len(c) <= 20), None)
        results.append({
            "testName": cells[0][:255],
            "valueText": cells[1],
            "unit": unit,
            "referenceRangeText": reference[:100] if reference else None,
            "pageNumber": page,
        })
    return results[:200]


def _medications(lines: list[tuple[int, str]]) -> list[dict]:
    meds = []
    for _, line in lines:
        dose = _DOSE.search(line)
        if not dose:
            continue
        head = _MED_PREFIX.sub("", line[: dose.start()]).strip(" -—:")
        if not re.match(r"^[A-Za-z][A-Za-z0-9 +/\-]{1,60}$", head):
            continue
        frequency = _FREQUENCY.search(line)
        duration = _DURATION.search(line)
        instructions = _INSTRUCTIONS.search(line)
        meds.append({
            "name": head,
            "dosage": dose.group(1),
            "frequency": frequency.group(1) if frequency else None,
            "route": None,
            "duration": duration.group(1) if duration else None,
            "instructions": instructions.group(1) if instructions else None,
        })
    return meds[:50]


def _title(doc_type: str, lines: list[tuple[int, str]]) -> str | None:
    if doc_type == "LAB_REPORT":
        for _, line in lines:
            cleaned = line.strip()
            if re.search(r"\b(count|panel|profile|test|function|lipid|thyroid)\b", cleaned, re.I) and len(_cells(cleaned)) == 1 and len(cleaned) <= 80:
                return re.sub(r"\s*\([^)]*\)\s*$", "", cleaned)
        return "Lab report"
    return {
        "PRESCRIPTION": "Prescription",
        "DISCHARGE_SUMMARY": "Discharge summary",
        "IMAGING_REPORT": "Imaging report",
        "CONSULTATION_NOTE": "Consultation note",
    }.get(doc_type)


def _join(names: list[str]) -> str:
    return names[0] if len(names) == 1 else ", ".join(names[:-1]) + " and " + names[-1]


def _summary(doc_type, doc_date, provider, facility, title, labs, meds) -> str:
    """Strictly descriptive: says what the document contains, never what it means."""
    label = _TYPE_LABEL[doc_type]
    first = f"This is {'an' if label[0] in 'aeiou' else 'a'} {label}"
    if facility:
        first += f" from {facility}"
    if doc_date:
        first += f" dated {doc_date.day} {doc_date.strftime('%B %Y')}"
    sentences = [first + "."]
    if provider:
        sentences.append(f"It names {provider} as the doctor.")
    if labs:
        names = [lab["testName"] for lab in labs[:6]]
        more = f" and {len(labs) - 6} more" if len(labs) > 6 else ""
        sentences.append(f"It lists {len(labs)} test result{'s' if len(labs) != 1 else ''}: {_join(names)}{more}, each as printed by the laboratory.")
    if meds:
        names = [med["name"] for med in meds[:6]]
        sentences.append(f"It lists {len(meds)} medicine{'s' if len(meds) != 1 else ''}: {_join(names)}.")
    if title and doc_type == "LAB_REPORT" and title != "Lab report":
        sentences.insert(1, f"The test performed is: {title}.")
    return " ".join(sentences)[:800]


def _terms(text: str) -> list[dict]:
    found = []
    for term, definition in _TERMS.items():
        if re.search(rf"\b{re.escape(term)}", text, re.I):
            found.append({"term": term, "definition": definition})
    return found[:8]


