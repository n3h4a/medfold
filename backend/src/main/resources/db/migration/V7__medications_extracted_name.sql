-- The medicine name as first read from the document. Corrections change "name" but not this,
-- so reprocessing can still recognise a corrected medicine and not add it a second time.
ALTER TABLE medications ADD COLUMN extracted_name VARCHAR(255);
UPDATE medications SET extracted_name = name;
ALTER TABLE medications ALTER COLUMN extracted_name SET NOT NULL;
