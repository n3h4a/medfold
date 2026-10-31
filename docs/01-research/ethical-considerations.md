# Ethical considerations

Medfold puts software between people and their medical records. These are the risks we
identified and how the design answers each one.

| Risk | Why it matters | How Medfold handles it |
|---|---|---|
| **The AI interprets results** ("your sugar is too high") | People may act on, or ignore, a software judgment about their health | The extraction schema has no field for diagnosis, risk or advice; free text is checked for such wording and rewritten if needed ([ADR-007](../03-design/adr/ADR-007-ai-safety-boundary.md)). Every page says Medfold gives no medical advice |
| **A misread value looks authoritative** | OCR and models make mistakes; a wrong number shown confidently is worse than none | Values are copied as printed; the original is shown next to them; comparisons are only made when a value and range parse with certainty; any row can be corrected or removed |
| **Range comparison read as a verdict** | "High" sounds like a diagnosis | The UI says "Above printed range", compares only with the range on the same report, and explains that it is not a medical judgment |
| **Hallucinated data** | A model can invent a test or medicine that isn't in the document | Output must validate against a strict schema; every run's raw output, model and prompt version are stored for audit |
| **Document text steering the model** (prompt injection) | A crafted document could try to make the model write advice or leak data | Page text is wrapped as data the document can't close or fake; the safety check runs regardless of what the model was told |
| **Sending health data to a third party** | Using a hosted LLM moves page text outside the user's server | Configurable: the offline reader keeps everything local; the docs state plainly what `LLM_PROVIDER=anthropic` sends |
| **Losing control of one's data** | Records are personal | Download everything as JSON; delete the account and every file; no analytics or tracking |
| **Unequal accuracy** | OCR works best on clean, English, printed documents; handwriting and other scripts do worse | Stated in [KNOWN_LIMITATIONS.md](../../KNOWN_LIMITATIONS.md); more languages are on the [roadmap](../../ROADMAP.md) |
| **Over-reliance** | A tidy dashboard can feel like a complete record | The timeline shows only what was uploaded; nothing is inferred to fill gaps |

## Principles we hold to

1. **Describe, never judge.** Medfold summarises what a document contains, not what it means.
2. **Show the source.** Every extracted value can be checked against the original.
3. **The user corrects, and the correction wins**, including over later reprocessing.
4. **Least data, least sharing.** Nothing leaves the deployment unless the operator chooses an LLM
   provider that requires it.
5. **Be honest about limits** in the product and in this repository.
