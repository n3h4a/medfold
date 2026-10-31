# Landscape

How people keep their medical records today, and where Medfold fits. This compares categories of
tools, not specific products, and makes no market-size claims.

| Approach | Strengths | What's missing |
|---|---|---|
| **Paper folders and printouts** | No setup; the original document | Hard to search; values can't be compared over time; easy to lose |
| **Files in email or a cloud drive** | Searchable by file name; backed up | Contents aren't structured; results from different labs never line up |
| **Patient portals of a hospital or lab** | Official data, often structured | One portal per provider; records from other places aren't there; access can end when you switch providers |
| **Phone health apps** | Good for device data (steps, heart rate) and, where supported, provider integrations | Paper and PDF records from providers without an integration mean manual entry |
| **General note-taking apps** | Flexible | Everything is typed by hand; no understanding of lab values or units |

## Where Medfold fits

Medfold starts from what people already have: the PDFs and paper they were given. It reads them
automatically, keeps the original next to the extracted data, and lines results up across labs
and units. It complements portals and phone apps rather than replacing them; importing from them
(e.g. FHIR) is on the [roadmap](../../ROADMAP.md).

## What we deliberately don't do

- **Interpret results or give advice:** that is a clinician's role ([ethical considerations](ethical-considerations.md)).
- **Lock data in:** export is one click, and the format is documented.
