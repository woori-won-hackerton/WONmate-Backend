-- Existing databases only. No documents, chunks or embeddings are deleted.
BEGIN;
ALTER TABLE public.product_documents
    DROP COLUMN IF EXISTS document_version,
    DROP COLUMN IF EXISTS effective_date;
COMMIT;
