"""
Generates the synthetic medical documents used for demos, screenshots and end-to-end tests.

Every document is invented and says so on the page. Run from the repository root:

    python tests/test-data/generate_samples.py

Needs reportlab and Pillow (both in the AI service's dev dependencies).
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

TESTS = Path(__file__).resolve().parent.parent
REPORTS = TESTS / "sample-pdfs"
IMAGES = TESTS / "sample-images"

LAB = "Medfold Demo Diagnostics Laboratory"
CLINIC = "Medfold Demo Clinic"
PATIENT = "Patient: Demo Patient (synthetic)    Age/Sex: 34 / F"
FOOTER = "SYNTHETIC DEMO DOCUMENT - invented for Medfold. Not a real person."

# Metabolic panel at four dates, so trends have something to show.
METABOLIC = [
    ("2024-11-08", {"hba1c": "5.6", "fbs": "96", "chol": "212", "ldl": "138", "hdl": "44", "tg": "168"}),
    ("2025-05-14", {"hba1c": "5.9", "fbs": "104", "chol": "205", "ldl": "131", "hdl": "46", "tg": "152"}),
    ("2025-11-20", {"hba1c": "6.2", "fbs": "112", "chol": "194", "ldl": "118", "hdl": "49", "tg": "139"}),
    ("2026-05-18", {"hba1c": "5.8", "fbs": "99", "chol": "181", "ldl": "104", "hdl": "53", "tg": "121"}),
]


def _page(path: Path, header: str, lines: list[tuple[str, ...]], columns=(50, 240, 330, 420)) -> None:
    # invariant=1: no timestamps or random ids, so regenerating gives byte-identical files.
    c = canvas.Canvas(str(path), pagesize=A4, invariant=1)
    c.setTitle(path.stem)
    c.setFont("Helvetica-Bold", 15)
    c.drawString(50, 800, header)
    y = 772
    for row in lines:
        if len(row) == 1:
            c.setFont("Helvetica-Bold" if row[0].isupper() or row[0].endswith(":") else "Helvetica", 11)
            c.drawString(50, y, row[0])
        else:
            c.setFont("Helvetica", 10.5)
            for x, cell in zip(columns, row):
                c.drawString(x, y, cell)
        y -= 20
    c.setFont("Helvetica-Oblique", 8)
    c.drawString(50, 40, FOOTER)
    c.save()


def metabolic(day: str, v: dict) -> None:
    _page(REPORTS / f"metabolic-panel-{day}.pdf", LAB, [
        (PATIENT,),
        (f"Sample collected: {day}    Report date: {day}",),
        ("Referred by: Dr. Meera Iyer",),
        ("",),
        ("Metabolic Panel",),
        ("Test", "Result", "Unit", "Reference range"),
        ("HbA1c", v["hba1c"], "%", "4.0 - 5.6"),
        ("Fasting Blood Sugar", v["fbs"], "mg/dL", "70 - 99"),
        ("Total Cholesterol", v["chol"], "mg/dL", "< 200"),
        ("LDL Cholesterol", v["ldl"], "mg/dL", "< 130"),
        ("HDL Cholesterol", v["hdl"], "mg/dL", "> 40"),
        ("Triglycerides", v["tg"], "mg/dL", "< 150"),
    ])


def blood_count() -> None:
    _page(REPORTS / "complete-blood-count-2026-03-02.pdf", LAB, [
        (PATIENT,),
        ("Sample collected: 2026-03-02    Report date: 2026-03-02",),
        ("Referred by: Dr. Meera Iyer",),
        ("",),
        ("Complete Blood Count (CBC)",),
        ("Test", "Result", "Unit", "Reference range"),
        ("Haemoglobin", "11.4", "g/dL", "12.0 - 15.5"),
        ("Total WBC Count", "7,400", "/cumm", "4,000 - 11,000"),
        ("RBC Count", "4.3", "million/cumm", "3.8 - 5.2"),
        ("Platelet Count", "2.6", "lakh/cumm", "1.5 - 4.5"),
        ("Hematocrit", "35.1", "%", "36 - 46"),
        ("ESR", "18", "mm/hr", "0 - 20"),
    ])


def prescription() -> None:
    _page(REPORTS / "prescription-2026-05-20.pdf", CLINIC, [
        ("Dr. Meera Iyer, MD (General Medicine)",),
        (PATIENT,),
        ("Visit date: 2026-05-20",),
        ("",),
        ("Prescription:",),
        ("1. Tab. Metformin 500 mg    1-0-1    after food    90 days",),
        ("2. Tab. Ferrous Sulphate 200 mg    1-0-0    after breakfast    60 days",),
        ("3. Cap. Vitamin D3 60000 IU    once weekly    8 weeks",),
        ("",),
        ("Follow-up visit after 3 months with a repeat metabolic panel.",),
    ])


def imaging() -> None:
    _page(REPORTS / "chest-x-ray-2025-08-11.pdf", "Medfold Demo Imaging Clinic", [
        (PATIENT,),
        ("Report date: 2025-08-11    Referred by: Dr. Rohan Mehta",),
        ("",),
        ("X-RAY CHEST PA VIEW",),
        ("Technique: Single frontal view of the chest.",),
        ("Lungs: Both lung fields are clear.",),
        ("Heart: Cardiac silhouette is within normal size.",),
        ("Bones: Visualised bony thorax appears unremarkable.",),
    ])


def photo() -> None:
    """A phone-photo-like image of a prescription, for the OCR path (needs Tesseract)."""
    image = Image.new("RGB", (1400, 900), (250, 248, 240))
    draw = ImageDraw.Draw(image)
    try:
        font = ImageFont.truetype("DejaVuSans.ttf", 34)
        bold = ImageFont.truetype("DejaVuSans-Bold.ttf", 40)
    except OSError:
        font = bold = ImageFont.load_default()
    lines = [
        (bold, CLINIC), (font, "Dr. Rohan Mehta"), (font, "Visit date: 2026-07-09"), (font, ""),
        (font, "Tab. Amlodipine 5 mg   0-0-1   30 days"), (font, "Tab. Pantoprazole 40 mg   1-0-0   before breakfast   14 days"),
        (font, ""), (font, "Synthetic demo document - not a real patient."),
    ]
    y = 80
    for f, text in lines:
        draw.text((80, y), text, fill=(30, 30, 40), font=f)
        y += 70
    image.rotate(1.2, expand=True, fillcolor=(235, 232, 225)).save(IMAGES / "prescription-photo-2026-07-09.png")


if __name__ == "__main__":
    REPORTS.mkdir(exist_ok=True)
    IMAGES.mkdir(exist_ok=True)
    for day, values in METABOLIC:
        metabolic(day, values)
    blood_count()
    prescription()
    imaging()
    photo()
    print(f"Wrote {len(list(REPORTS.glob('*.pdf')))} PDFs to {REPORTS} and 1 image to {IMAGES}")
