# Glossary

## Medical terms used in the app and the sample data

| Term | Meaning |
|---|---|
| **CBC** (complete blood count) | A common blood test counting red and white blood cells and platelets |
| **Haemoglobin (Hb)** | The protein in red blood cells that carries oxygen |
| **HbA1c** | A blood test reflecting average blood sugar over roughly the past two to three months; printed as % (NGSP) or mmol/mol (IFCC) |
| **Fasting blood sugar (FBS)** | Blood glucose measured after not eating, usually overnight |
| **LDL / HDL cholesterol** | Two kinds of cholesterol carried in the blood |
| **Triglycerides** | A type of fat measured in the blood |
| **TSH** | Thyroid-stimulating hormone, measured to check thyroid function |
| **Reference range** | The range a laboratory prints next to a result; ranges differ between labs and methods |
| **mg/dL, mmol/L** | Mass and molar concentration units; converting between them depends on the substance |
| **/cumm** | Per cubic millimetre, the same as per microlitre (µL) |
| **1-0-1** | Prescription shorthand for doses morning-afternoon-night |

Medfold shows these as written and explains terms in neutral words; it never interprets results.

## Technical terms

| Term | Meaning |
|---|---|
| **ADR** | Architecture Decision Record: a short note on why a design choice was made ([list](../03-design/adr/README.md)) |
| **Access token / refresh token** | Short-lived proof of login sent with each request / long-lived cookie used to get new access tokens |
| **Reuse detection** | Treating a second use of a rotated refresh token as theft and ending every session |
| **Owner-scoped query** | A database query that always includes the owner's id, so it can't return another user's data |
| **OCR** | Optical character recognition: reading text from images (Tesseract here) |
| **Text layer** | The real text inside a digital PDF, read exactly without OCR |
| **Extraction** | Turning page text into structured data (type, date, results, medicines) |
| **Test key** | Canonical id for a lab test (`hba1c`) so results from different labs line up |
| **Printed-range comparison** | Medfold's "below / within / above printed range" label, computed from the same report's range |
| **Job queue** | Table of pending work for the background worker |
| **`SKIP LOCKED`** | PostgreSQL option that lets concurrent workers skip rows another worker has locked |
| **Prompt injection** | Text inside a document that tries to give the language model instructions |
| **CSP** | Content-Security-Policy: a header limiting what a page may load or run |
| **HSTS** | Strict-Transport-Security: tells browsers to use HTTPS only |
| **p95** | 95th percentile: 95% of requests were at least this fast |
