# ADR-007: The AI describes and copies; anything evaluative is deterministic

**Status:** Accepted

## Context

A language model reading medical documents can hallucinate values, interpret results ("your
sugar is high"), or give advice, and document text can try to instruct the model (prompt
injection). Medfold is a record keeper, not a medical device, and users must be able to trust
that what they see is what the document says.

## Decision

1. **The schema is the boundary.** The model fills `ExtractionResult`: type, date, title, doctor,
   facility, a short descriptive summary, term definitions, lab rows and medicines. There is no
   field for diagnosis, risk, interpretation or advice, and unknown fields are rejected at every
   level (`extra="forbid"`). A test scans the schema for such field names.
2. **Copy, don't compute.** Values, units and ranges are copied as printed. High/low comparison is
   done by the backend (`LabFlagCalculator`) against the range printed on the same report, and is
   left blank when anything is uncertain. Test keys for trends come from a deterministic synonym
   table, not the model.
3. **Validate, then check wording.** Output is validated with Pydantic; invalid output gets one
   corrective retry. Free text is scanned for advice, diagnosis and judgment wording; unsafe text
   gets a retry, then is replaced by a summary rebuilt from extracted facts.
4. **Treat documents as data.** Page text is wrapped in tags the document can't close, and the
   prompt says document text is never instructions.
5. **Keep an audit trail.** Every run stores model, prompt version, safety outcome and raw output.

## Alternatives considered

| Option | Why not |
|---|---|
| Let the model flag abnormal values and explain them | Clinical interpretation, unverifiable, and wrong whenever the model misreads a range |
| Free-form model output, parsed afterwards | No guarantee of shape; injection and hallucinated fields slip through |
| No AI: rules only | Works for clean digital reports (it's the offline `mock` provider), but misses layout variety |

## Consequences

- Users see values exactly as printed, a neutral comparison with the printed range, and the
  original document next to them; every page says it isn't medical advice.
- Some useful things are deliberately out of scope (e.g. "this trend is worrying").
- Switching models is configuration only, and the safety layer doesn't depend on the model behaving.
