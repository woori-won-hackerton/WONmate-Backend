-- PostgreSQL 개발/시연 전용. demo-data.sql 실행 후 파일 전체를 수동 실행하세요.
-- monthlyPremium은 payment_frequency='monthly'일 때 contracts.actual_premium입니다.
-- 실제 상품 보험료가 아닌 임의 값이며, DB 접속 정보는 .env 설정을 사용하세요.
-- 연결 상품의 납입주기를 변경하므로 같은 상품의 다른 계약에도 월납 설정이 적용됩니다.
BEGIN;

-- 시연 FC의 계약만 대상으로 고정된 임의 보험료(30,000~90,000원)를 설정합니다.
-- 같은 계약은 재실행해도 동일한 값이 들어갑니다.
UPDATE contracts c
SET actual_premium = 30000.00 + (c.contract_id % 7) * 10000.00,
    updated_at = CURRENT_TIMESTAMP
FROM fc_customers fc
JOIN financial_consultants f ON f.fc_id = fc.fc_id
WHERE c.fc_customer_id = fc.fc_customer_id
  AND f.login_id = 'demo_fc'
  AND f.email = 'wonmate-demo@example.invalid';

UPDATE insurance_products p
SET payment_frequency = 'monthly'::payment_frequency,
    updated_at = CURRENT_TIMESTAMP
WHERE p.payment_frequency IS DISTINCT FROM 'monthly'::payment_frequency
  AND EXISTS (
      SELECT 1
      FROM contracts c
      JOIN fc_customers fc ON fc.fc_customer_id = c.fc_customer_id
      JOIN financial_consultants f ON f.fc_id = fc.fc_id
      WHERE c.product_id = p.product_id
        AND f.login_id = 'demo_fc'
        AND f.email = 'wonmate-demo@example.invalid'
  );

SELECT fc.customer_id, c.contract_id, p.product_name,
       p.payment_frequency,
       CASE WHEN p.payment_frequency = 'monthly'
            THEN c.actual_premium END AS "monthlyPremium"
FROM contracts c
JOIN insurance_products p ON p.product_id = c.product_id
JOIN fc_customers fc ON fc.fc_customer_id = c.fc_customer_id
JOIN financial_consultants f ON f.fc_id = fc.fc_id
WHERE f.login_id = 'demo_fc'
  AND f.email = 'wonmate-demo@example.invalid'
ORDER BY fc.customer_id, c.contract_id;

COMMIT;
