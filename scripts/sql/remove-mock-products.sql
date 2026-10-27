-- Development cleanup: remap contracts, preserve their IDs/terms, remove only known mock products.
-- Document evidence is never reassigned to a different product.
BEGIN;
LOCK TABLE insurance_products, contracts, product_documents IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE mock_product_replacements (
    old_id bigint PRIMARY KEY,
    new_id bigint NOT NULL
) ON COMMIT DROP;

DO $$
DECLARE
    fixture record;
    source_id bigint;
    target_id bigint;
BEGIN
    FOR fixture IN SELECT * FROM (VALUES
        ('[MOCK-CONTRACT] 건강보험', '무배당우리WON하는유니버셜종신보험'),
        ('[MOCK-CONTRACT] 암보험', '무배당우리WON하는암보험'),
        ('[MOCK-CONTRACT] 상해보험', '무배당엔젤쑥쑥어린이보장보험'),
        ('[MOCK-CONTRACT] 연납보험', '무배당우리WON하는5배더행복한플러스종신보험')
    ) AS mapping(old_name, new_name) LOOP
        IF NOT EXISTS (SELECT 1 FROM insurance_products WHERE product_name=fixture.old_name) THEN
            CONTINUE;
        END IF;
        SELECT product_id INTO STRICT target_id FROM insurance_products p
        WHERE p.product_name=fixture.new_name
          AND EXISTS (SELECT 1 FROM product_documents d JOIN product_chunks c USING (document_id)
              WHERE d.product_id=p.product_id AND d.ingestion_status='COMPLETED'
                AND d.embedding_model='text-embedding-3-small' AND c.embedding IS NOT NULL);
        FOR source_id IN SELECT product_id FROM insurance_products WHERE product_name=fixture.old_name LOOP
            INSERT INTO mock_product_replacements VALUES (source_id, target_id);
        END LOOP;
    END LOOP;
    IF EXISTS (SELECT 1 FROM product_documents d JOIN mock_product_replacements m ON m.old_id=d.product_id) THEN
        RAISE EXCEPTION 'Mock products have document evidence; cleanup aborted without reassigning documents';
    END IF;
END $$;

UPDATE contracts c SET product_id=m.new_id, updated_at=CURRENT_TIMESTAMP
FROM mock_product_replacements m WHERE c.product_id=m.old_id;

DELETE FROM insurance_products p USING mock_product_replacements m WHERE p.product_id=m.old_id;
COMMIT;
