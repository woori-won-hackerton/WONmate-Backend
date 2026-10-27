SELECT current_database(), current_user, version();
SELECT name, default_version, installed_version
FROM pg_available_extensions WHERE name = 'vector';
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

SELECT c.relname AS table_name, a.attname AS column_name,
       format_type(a.atttypid, a.atttypmod) AS actual_type, a.attnotnull AS not_null
FROM pg_attribute a
JOIN pg_class c ON c.oid = a.attrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relname IN ('product_documents', 'product_chunks')
  AND a.attnum > 0 AND NOT a.attisdropped
ORDER BY c.relname, a.attnum;

SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid IN ('public.product_documents'::regclass, 'public.product_chunks'::regclass)
ORDER BY conrelid, conname;

-- Assert critical schema requirements, fail rather than merely display missing rows.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'vector extension is not enabled';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = 'public.product_chunks'::regclass AND attname = 'embedding'
          AND format_type(atttypid, atttypmod) = 'vector(1536)' AND NOT attnotnull
    ) THEN
        RAISE EXCEPTION 'embedding must be nullable vector(1536)';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.product_chunks'::regclass
          AND contype = 'u' AND pg_get_constraintdef(oid) = 'UNIQUE (document_id, chunk_index)'
    ) THEN
        RAISE EXCEPTION 'document/chunk index unique constraint is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.product_documents'::regclass
                   AND confrelid = 'public.insurance_products'::regclass AND contype = 'f')
       OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.product_chunks'::regclass
                   AND confrelid = 'public.product_documents'::regclass AND contype = 'f') THEN
        RAISE EXCEPTION 'RAG foreign key relationship is missing';
    END IF;
END $$;
