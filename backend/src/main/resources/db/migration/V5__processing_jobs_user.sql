-- Jobs record their document's owner, so the worker can load documents through the same
-- owner-scoped queries as the rest of the app (there is deliberately no unscoped document lookup).
ALTER TABLE processing_jobs ADD COLUMN user_id UUID;
UPDATE processing_jobs j SET user_id = d.user_id FROM documents d WHERE d.id = j.document_id;
ALTER TABLE processing_jobs ALTER COLUMN user_id SET NOT NULL;
