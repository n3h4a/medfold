"""
Maps the many ways labs print the same test to one key, so results from different labs line up
over time (e.g. "HbA1c", "Glycated Haemoglobin" and "Glycosylated hemoglobin (A1c)" are all "hba1c").
Deterministic on purpose: trends must not depend on what a language model guessed.
"""

import re
import unicodedata

_SYNONYMS: dict[str, tuple[str, ...]] = {
    "hba1c": ("hba1c", "a1c", "glycated haemoglobin", "glycated hemoglobin", "glycosylated haemoglobin",
              "glycosylated hemoglobin", "haemoglobin a1c", "hemoglobin a1c"),
    "haemoglobin": ("haemoglobin", "hemoglobin", "hb", "hgb"),
    "wbc": ("wbc", "total wbc count", "wbc count", "total leucocyte count", "total leukocyte count", "tlc",
            "white blood cells", "white blood cell count"),
    "rbc": ("rbc", "rbc count", "red blood cells", "red blood cell count", "total rbc count"),
    "platelets": ("platelets", "platelet count", "plt"),
    "hematocrit": ("hematocrit", "haematocrit", "hct", "pcv", "packed cell volume"),
    "glucose_fasting": ("fasting blood sugar", "fbs", "fasting glucose", "fasting plasma glucose", "glucose fasting",
                        "blood sugar fasting", "fpg"),
    "glucose_pp": ("post prandial blood sugar", "ppbs", "postprandial glucose", "glucose pp", "blood sugar pp",
                   "post prandial glucose"),
    "glucose_random": ("random blood sugar", "rbs", "random glucose", "glucose random"),
    "cholesterol_total": ("total cholesterol", "cholesterol total", "cholesterol", "serum cholesterol"),
    "ldl": ("ldl", "ldl cholesterol", "ldl c", "low density lipoprotein"),
    "hdl": ("hdl", "hdl cholesterol", "hdl c", "high density lipoprotein"),
    "triglycerides": ("triglycerides", "tg", "serum triglycerides"),
    "tsh": ("tsh", "thyroid stimulating hormone", "tsh ultrasensitive", "ultrasensitive tsh"),
    "t3": ("t3", "total t3", "triiodothyronine"),
    "t4": ("t4", "total t4", "thyroxine"),
    "creatinine": ("creatinine", "serum creatinine", "s creatinine"),
    "urea": ("urea", "blood urea", "serum urea"),
    "uric_acid": ("uric acid", "serum uric acid"),
    "vitamin_d": ("vitamin d", "25 oh vitamin d", "25 hydroxy vitamin d", "vitamin d3", "vitamin d total",
                  "25 oh vitamin d total"),
    "vitamin_b12": ("vitamin b12", "b12", "cyanocobalamin", "vitamin b 12"),
    "alt": ("alt", "sgpt", "alanine aminotransferase", "alt sgpt", "sgpt alt"),
    "ast": ("ast", "sgot", "aspartate aminotransferase", "ast sgot", "sgot ast"),
    "bilirubin_total": ("total bilirubin", "bilirubin total", "serum bilirubin total"),
    "esr": ("esr", "erythrocyte sedimentation rate"),
    "crp": ("crp", "c reactive protein"),
    "ferritin": ("ferritin", "serum ferritin"),
    "sodium": ("sodium", "na", "serum sodium"),
    "potassium": ("potassium", "k", "serum potassium"),
}

_LOOKUP = {alias: key for key, aliases in _SYNONYMS.items() for alias in aliases}
_NON_ALNUM = re.compile(r"[^a-z0-9]+")
_BRACKETS = re.compile(r"\([^)]*\)")


def _normalize(name: str) -> str:
    text = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().lower()
    return _NON_ALNUM.sub(" ", text).strip()


def lab_test_key(test_name: str) -> str:
    """A stable key for a printed test name: a known canonical key, or a slug of the name."""
    normalized = _normalize(test_name)
    if normalized in _LOOKUP:
        return _LOOKUP[normalized]
    without_brackets = _normalize(_BRACKETS.sub(" ", test_name))
    if without_brackets in _LOOKUP:
        return _LOOKUP[without_brackets]
    slug = normalized.replace(" ", "_")
    return slug[:100] or "unknown"
