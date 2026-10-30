"""
Last line of defence for the "describe, never interpret" rule.

The schema gives the model no field for diagnosis or advice, but free text (the summary and term
definitions) could still say it. This scans that text for diagnosis, advice and value-judgment
wording. Hits trigger a corrective retry; if the model still won't comply, the unsafe text is
replaced or dropped rather than shown to the user.
"""

import re
from dataclasses import dataclass
from enum import Enum

from app.extraction.schemas import ExtractionResult, TermExplanation


class SafetyCategory(str, Enum):
    ADVICE = "ADVICE"
    DIAGNOSIS = "DIAGNOSIS"
    JUDGMENT = "JUDGMENT"


@dataclass(frozen=True)
class SafetyIssue:
    field: str
    category: SafetyCategory
    phrase: str

    def describe(self) -> str:
        return f"{self.field} contains {self.category.value.lower()} wording (\"{self.phrase}\")"


def _rx(*patterns: str) -> re.Pattern:
    return re.compile("|".join(f"(?:{p})" for p in patterns), re.IGNORECASE)


# Telling the reader what to do. Never allowed.
_ADVICE = _rx(
    r"\byou (?:should|must|need to|ought to|may want to|might want to|can try)\b",
    r"\b(?:we|i) (?:recommend|suggest|advise)\b",
    r"\b(?:is|are) (?:recommended|advised|advisable)\b",
    r"\bit is (?:important|advisable|best|wise) to\b",
    r"\bconsider (?:taking|stopping|starting|reducing|increasing|changing|seeing|asking)\b",
    r"\b(?:talk|speak|check) (?:to|with) (?:your|a) (?:doctor|physician|gp)\b",
    r"\bconsult (?:your|a) (?:doctor|physician|specialist|gp)\b",
    r"\b(?:stop|start|keep|avoid|continue) taking\b",
    r"\bmake sure (?:to|you)\b",
    r"\bplease (?:see|contact|visit|consult)\b",
)

# Conclusions about the person's health. Allowed only when the sentence reports what the document
# or its author says ("The discharge summary lists the final diagnosis as ...").
_DIAGNOSIS_ALWAYS = _rx(
    r"\byou (?:may|might|could|probably|likely|seem to|appear to)? ?(?:have|suffer from)\b",
    r"\byour (?:results?|values?|levels?|report|tests?) (?:suggest|indicate|show|point|mean)s?\b",
    r"\b(?:you are|you're) (?:diabetic|anaemic|anemic|prediabetic|pre-diabetic|deficient|at risk)\b",
)
_DIAGNOSIS = _rx(
    r"\b(?:suggests?|suggestive of|indicates?|indicative of|consistent with|points? to|signs? of|sign of)\b",
    r"\bdiagnos(?:is|es|ed|e)\b",
    r"\b(?:likely|probable|possible) (?:cause|diagnosis|condition)\b",
    r"\bat (?:high|increased|low) risk\b",
)
# Verdicts on values. The app compares results with the report's own range; the model must not.
_JUDGMENT = _rx(
    r"\b(?:ab)?normal\b",
    r"\b(?:elevated|raised|decreased|deranged|borderline|worrying|concerning|alarming|reassuring|"
    r"dangerous|critical|healthy|unhealthy|optimal|suboptimal|out of range)\b",
    r"\b(?:high|low)\b(?!-)",
    r"\bwithin (?:the )?(?:normal|reference|healthy) (?:limits|range)\b",
    r"\b(?:above|below|outside) (?:the )?(?:normal|reference|healthy) (?:range|limits?)\b",
    r"\b(?:good|bad) news\b",
    r"\bnothing to worry\b",
)

# Narrow, factual phrasings that report what the document itself says. Only these exact phrases are
# exempt (they are removed before scanning); the rest of the sentence is still checked, so
# "This report lists haemoglobin, which is low" is still caught.
_REPORTED_FACTS = _rx(
    r"\b(?:lists?|records?|states?|notes?|mentions?|gives?|documents?)\s+(?:the\s+|a\s+|an\s+)?"
    r"(?:final\s+|provisional\s+|working\s+|admission\s+|discharge\s+|primary\s+|secondary\s+)?diagnos(?:is|es)\b",
    r"\b(?:marked|flagged|printed|labelled|labeled)\s+(?:as\s+|with\s+)?[\"'‘“]?(?:high|low|abnormal|h|l)\b[\"'’”]?",
)
_SENTENCE_SPLIT = re.compile(r"(?<=[.!?])\s+")


def _without_names(text: str, names: list[str]) -> str:
    """Removes proper names (e.g. 'City Diagnostics', 'Healthy Life Clinic', test names) before scanning."""
    for name in sorted((n for n in names if n), key=len, reverse=True):
        text = re.sub(re.escape(name), " ", text, flags=re.IGNORECASE)
    return text


_CHECKS = [
    (_ADVICE, SafetyCategory.ADVICE),
    (_DIAGNOSIS_ALWAYS, SafetyCategory.DIAGNOSIS),
    (_DIAGNOSIS, SafetyCategory.DIAGNOSIS),
    (_JUDGMENT, SafetyCategory.JUDGMENT),
]


def _scan(text: str, field: str, names: list[str], allow_reported_facts: bool) -> list[SafetyIssue]:
    """At most one issue per sentence (the most serious category first)."""
    issues: list[SafetyIssue] = []
    for sentence in _SENTENCE_SPLIT.split(_without_names(text, names)):
        if allow_reported_facts:
            sentence = _REPORTED_FACTS.sub(" ", sentence)
        for pattern, category in _CHECKS:
            match = pattern.search(sentence)
            if match:
                issues.append(SafetyIssue(field, category, match.group(0).strip()))
                break
    return issues


def _names(result: ExtractionResult) -> list[str]:
    return [
        result.facility_name or "",
        result.provider_name or "",
        result.title or "",
        *(lab.test_name for lab in result.lab_results),
        *(med.name for med in result.medications),
    ]


def check_extraction(result: ExtractionResult) -> list[SafetyIssue]:
    """All unsafe phrasings in the model's own free text. Values copied from the document aren't scanned."""
    names = _names(result)
    issues = _scan(result.summary, "summary", names, allow_reported_facts=True)
    if result.title:
        issues += _scan(result.title, "title", names[:2], allow_reported_facts=False)
    for index, term in enumerate(result.terms_explained):
        # Definitions must be general, so the reported-facts exemption doesn't apply. The term itself
        # (e.g. "High-sensitivity CRP") may legitimately contain flagged words, so it is treated as a name.
        issues += _scan(term.definition, f"termsExplained[{index}].definition", names + [term.term], allow_reported_facts=False)
    return issues


_TYPE_LABEL = {
    "LAB_REPORT": "lab report",
    "PRESCRIPTION": "prescription",
    "DISCHARGE_SUMMARY": "discharge summary",
    "IMAGING_REPORT": "imaging report",
    "CONSULTATION_NOTE": "consultation note",
    "OTHER": "document",
}


def neutral_summary(result: ExtractionResult) -> str:
    """A summary built only from extracted facts, used when the model's own summary isn't safe."""
    sentence = f"This is a {_TYPE_LABEL[result.doc_type.value]}"
    if result.facility_name:
        sentence += f" from {result.facility_name}"
    if result.doc_date:
        sentence += f" dated {result.doc_date.day} {result.doc_date.strftime('%B %Y')}"
    parts = [sentence + "."]
    if result.lab_results:
        parts.append(f"It lists {len(result.lab_results)} test result{'s' if len(result.lab_results) != 1 else ''}.")
    if result.medications:
        parts.append(f"It lists {len(result.medications)} medicine{'s' if len(result.medications) != 1 else ''}.")
    return " ".join(parts)


def sanitize(result: ExtractionResult, issues: list[SafetyIssue]) -> ExtractionResult:
    """Replaces an unsafe summary or title and drops unsafe definitions; all extracted data is kept."""
    fields = {issue.field for issue in issues}
    terms: list[TermExplanation] = [
        term for index, term in enumerate(result.terms_explained) if f"termsExplained[{index}].definition" not in fields
    ]
    return result.model_copy(
        update={
            "summary": neutral_summary(result) if "summary" in fields else result.summary,
            "title": None if "title" in fields else result.title,
            "terms_explained": terms,
        }
    )
