import pytest

from app.extraction.lab_keys import lab_test_key


@pytest.mark.parametrize(
    ("printed", "key"),
    [
        ("HbA1c", "hba1c"),
        ("Glycated Haemoglobin (HbA1c)", "hba1c"),
        ("GLYCOSYLATED HEMOGLOBIN", "hba1c"),
        ("Haemoglobin", "haemoglobin"),
        ("Hemoglobin (Hb)", "haemoglobin"),
        ("Total WBC count", "wbc"),
        ("Total Leucocyte Count (TLC)", "wbc"),
        ("Platelet count", "platelets"),
        ("Fasting Blood Sugar", "glucose_fasting"),
        ("S. Creatinine", "creatinine"),
        ("Vitamin D (25-OH)", "vitamin_d"),
        ("SGPT (ALT)", "alt"),
        ("TSH - Ultrasensitive", "tsh"),
        ("LDL Cholesterol", "ldl"),
    ],
)
def test_known_tests_map_to_one_key(printed, key):
    assert lab_test_key(printed) == key


def test_unknown_tests_get_a_stable_slug():
    assert lab_test_key("Serum Lipase") == "serum_lipase"
    assert lab_test_key("  Serum   Lipase ") == "serum_lipase"
    assert lab_test_key("Anti-CCP Antibody") == "anti_ccp_antibody"
    assert lab_test_key("µ-Albumin/Creatinine ratio") == "albumin_creatinine_ratio"
    assert lab_test_key("???") == "unknown"
