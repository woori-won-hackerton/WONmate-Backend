-- Align legacy PostgreSQL enums with the current JPA STRING mappings.
-- Preserve rows; invalid values fail the transaction without partial changes.
BEGIN;

ALTER TABLE public.product_documents
    ALTER COLUMN document_type DROP DEFAULT,
    ALTER COLUMN ingestion_status DROP DEFAULT;

ALTER TABLE public.product_documents
    ALTER COLUMN document_type TYPE varchar(20) USING document_type::text,
    ALTER COLUMN ingestion_status TYPE varchar(20) USING ingestion_status::text;

ALTER TABLE public.product_documents
    ALTER COLUMN ingestion_status SET DEFAULT 'PENDING';

ALTER TABLE public.product_documents
    DROP CONSTRAINT IF EXISTS ck_product_documents_type,
    DROP CONSTRAINT IF EXISTS ck_product_documents_status;

ALTER TABLE public.product_documents
    ADD CONSTRAINT ck_product_documents_type CHECK (document_type IN ('TERMS', 'SUMMARY')),
    ADD CONSTRAINT ck_product_documents_status CHECK (ingestion_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));

COMMIT;
