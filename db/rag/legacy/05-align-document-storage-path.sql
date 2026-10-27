-- Legacy databases with storage_uri: preserve existing paths and rows.
-- Run once before restarting ingestion. Safe to run again.
BEGIN;
ALTER TABLE public.product_documents ADD COLUMN IF NOT EXISTS storage_url text;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'product_documents'
          AND column_name = 'storage_uri'
    ) THEN
        UPDATE public.product_documents
        SET storage_url = storage_uri
        WHERE storage_url IS NULL;
        -- Current JPA writes storage_url. Keep the legacy column and its data.
        ALTER TABLE public.product_documents ALTER COLUMN storage_uri DROP NOT NULL;
    END IF;
END $$;
-- Reject incomplete existing records rather than inventing paths.
ALTER TABLE public.product_documents ALTER COLUMN storage_url SET NOT NULL;
COMMIT;
