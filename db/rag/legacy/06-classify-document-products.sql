-- Fill missing classifications only for explicitly listed document-backed products.
BEGIN;
UPDATE public.insurance_products p
SET insurance_type = categories.code
FROM (VALUES
    ('무배당엔젤쑥쑥어린이보장보험', 'CHILD'),
    ('무배당우리WON하는5배더행복한플러스종신보험', 'WHOLE_LIFE'),
    ('무배당우리WON하는7배더행복한플러스종신보험', 'WHOLE_LIFE'),
    ('무배당우리WON하는간편알뜰플러스종신보험', 'WHOLE_LIFE'),
    ('무배당우리WON하는꿈나무우리아이보험', 'CHILD'),
    ('무배당우리WON하는실속플러스하나로암보험', 'CANCER'),
    ('무배당우리WON하는실속하나로암보험', 'CANCER'),
    ('무배당우리WON하는쑥쑥어린이보장보험', 'CHILD'),
    ('무배당우리WON하는암보험', 'CANCER'),
    ('무배당우리WON하는유니버셜종신보험', 'WHOLE_LIFE')
) AS categories(product_name, code)
WHERE p.product_name=categories.product_name
  AND (p.insurance_type IS NULL OR btrim(p.insurance_type) IN ('', 'UNKNOWN'))
  AND EXISTS (SELECT 1 FROM public.product_documents d WHERE d.product_id=p.product_id);
COMMIT;
