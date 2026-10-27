-- PostgreSQL 시연 데이터 (모든 인물/연락처/상담 내용/계약 보험료는 가상 데이터)
-- 서버 최초 구동 및 상품 적재 완료 후 파일 전체를 수동 실행하세요.
-- 현재 JPA 엔티티로 생성/갱신된 스키마가 필요합니다.
-- application.yaml의 spring.sql.init.mode=never는 그대로 유지합니다.
-- 로그인: demo_fc / MockFc123!
-- 상품, 상품 문서, 청크는 조회만 하며 INSERT/UPDATE하지 않습니다.
-- 동일 파일 재실행 시 기존 시연 데이터를 유지합니다. 동시에 실행하지 마세요.
-- 날짜는 최초 실행일(한국 시간) 기준입니다. 재실행으로 일정을 초기화하지 않습니다.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Seoul';

DO $$
DECLARE
    v_fc bigint;
    v_customer bigint;
    v_relation bigint;
    v_consult bigint;
    v_previous bigint;
    v_preparation bigint;
    v_product bigint;
    r record;
BEGIN
    IF EXISTS (SELECT 1 FROM financial_consultants WHERE login_id = 'demo_fc'
               AND email <> 'wonmate-demo@example.invalid') THEN
        RAISE EXCEPTION 'demo_fc 로그인 ID가 이미 사용 중입니다.';
    END IF;
    -- 아래 상품명은 서버의 문서 적재 상품명과 일치해야 합니다.
    FOR r IN SELECT * FROM (VALUES
        ('무배당우리WON하는유니버셜종신보험'),
        ('무배당우리WON하는암보험'),
        ('무배당엔젤쑥쑥어린이보장보험')
    ) AS p(name) LOOP
        IF (SELECT count(*) FROM insurance_products WHERE product_name = r.name) <> 1 THEN
            RAISE EXCEPTION '상품 적재 후 실행하세요. 고유한 상품이 필요합니다: %', r.name;
        END IF;
    END LOOP;

    INSERT INTO financial_consultants
        (login_id, password, email, full_name, joined_year, branch_name, created_at, updated_at)
    VALUES ('demo_fc', '$2a$10$m0FymkrCzMqoMEgnQpULP.G3v8JKcix8MMfu2qalwcBe0dzDZG1iK',
        'wonmate-demo@example.invalid', '이서연', 2020, '우리금융 서울중앙지점', now(), now())
    ON CONFLICT (login_id) DO NOTHING;
    SELECT fc_id INTO STRICT v_fc FROM financial_consultants WHERE login_id = 'demo_fc';

    FOR r IN SELECT * FROM (VALUES
        (1, '김민준', DATE '1987-05-15', 'male', 'IT 기업 개발팀장', true, true, '서울 마포구',
         '010-0000-9101', 'CONTRACT_REVIEW', 'scheduled', 0, 10,
         '가족 보장 점검 및 암 보장 상담',
         '배우자와 초등학생 자녀 1명. 월 추가 보험료 예산 5만 원. 평일 오전 연락 선호. 기존 종신보험 유지 희망.',
         '무배당우리WON하는유니버셜종신보험', 85000, 'active'),
        (2, '박지우', DATE '1995-09-22', 'female', '초등학교 교사', false, false, '서울 성동구',
         '010-0000-9102', 'NEW', 'scheduled', 0, 14,
         '첫 보험 가입을 위한 보장 설계',
         '첫 보험 상담. 월 예산 7만 원 이내. 저축보다 질병 보장을 우선하며 어려운 용어는 쉬운 설명을 선호.',
         '무배당우리WON하는암보험', 42000, 'active'),
        (3, '이도윤', DATE '1978-03-08', 'male', '자영업', true, true, '경기 고양시',
         '010-0000-9103', 'RENEWAL', 'draft', 1, 11,
         '보험료 부담 및 계약 유지 여부 상담',
         '자영업 매출 변동으로 보험료 부담 우려. 해지보다 보장 유지 방법에 관심. 최근 미납 내역 확인 필요.',
         '무배당우리WON하는암보험', 58000, 'lapsed'),
        (4, '최하은', DATE '1990-11-12', 'female', '회사원', true, true, '서울 송파구',
         '010-0000-9104', 'CLAIM_GUIDANCE', 'scheduled', 2, 15,
         '자녀 보험금 청구 서류 안내',
         '자녀 보험 계약 관리 중. 청구 가능 여부와 구비 서류 문의. 실제 지급 여부는 약관과 심사 결과 확인 필요.',
         '무배당엔젤쑥쑥어린이보장보험', 32000, 'active'),
        (5, '정서준', DATE '1966-01-24', 'male', '퇴직자', true, true, '서울 은평구',
         '010-0000-9105', 'CONTRACT_REVIEW', 'cancelled', 3, 10,
         '은퇴 이후 보장 점검',
         '은퇴 이후 월 지출 관리에 관심. 개인 일정으로 상담 취소, 다음 주 재연락 요청.',
         '무배당우리WON하는유니버셜종신보험', 120000, 'terminated')
    ) AS f(n, name, birth, gender, job, married, children, area, phone, kind, state,
           day_offset, hour_offset, purpose, wiki, product, premium, contract_state)
    LOOP
        IF (SELECT count(*) FROM customers WHERE phone_number = r.phone) > 1 THEN
            RAISE EXCEPTION '시연용 연락처가 중복됩니다: %', r.phone;
        END IF;
        SELECT customer_id INTO v_customer FROM customers WHERE phone_number = r.phone;
        IF v_customer IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM fc_customers WHERE customer_id = v_customer AND fc_id = v_fc
        ) THEN
            RAISE EXCEPTION '시연용 연락처가 다른 고객에게 사용 중입니다: %', r.phone;
        END IF;
        IF v_customer IS NULL THEN
            INSERT INTO customers (full_name, birth_date, gender, occupation, is_married,
                has_children, has_disability, phone_number, residential_area, created_at, updated_at)
            VALUES (r.name, r.birth, r.gender::gender_code, r.job, r.married,
                r.children, false, r.phone, r.area, now(), now()) RETURNING customer_id INTO v_customer;
        END IF;
        INSERT INTO fc_customers (customer_id, fc_id, assignment_status, created_at, updated_at)
        VALUES (v_customer, v_fc, 'active', now(), now()) ON CONFLICT (fc_id, customer_id) DO NOTHING;
        SELECT fc_customer_id INTO STRICT v_relation FROM fc_customers
        WHERE customer_id = v_customer AND fc_id = v_fc;

        INSERT INTO customer_wikis (customer_id, content, created_at, updated_at)
        VALUES (v_customer, r.wiki, now(), now()) ON CONFLICT (customer_id) DO NOTHING;

        SELECT product_id INTO STRICT v_product FROM insurance_products WHERE product_name = r.product;
        IF NOT EXISTS (SELECT 1 FROM contracts WHERE fc_customer_id = v_relation AND product_id = v_product) THEN
            INSERT INTO contracts (fc_customer_id, product_id, actual_premium, start_date,
                maturity_date, status, created_at, updated_at)
            VALUES (v_relation, v_product, r.premium, CURRENT_DATE - INTERVAL '3 years',
                CURRENT_DATE + INTERVAL '17 years', r.contract_state::contract_status, now(), now());
        END IF;

        SELECT consultation_id INTO v_consult FROM consultations
        WHERE fc_customer_id = v_relation AND purpose = r.purpose ORDER BY consultation_id LIMIT 1;
        IF v_consult IS NULL THEN
            INSERT INTO consultations (fc_customer_id, scheduled_at, purpose, consult_type,
                status, memo, confirmed_at, created_at, updated_at)
            VALUES (v_relation, (CURRENT_DATE + r.day_offset + make_time(r.hour_offset, 0, 0)) AT TIME ZONE 'Asia/Seoul',
                r.purpose, r.kind, r.state::consultation_status, '',
                CASE WHEN r.state = 'draft' THEN NULL ELSE now() END, now(), now())
            RETURNING consultation_id INTO v_consult;
        END IF;

        -- 첫 고객은 과거 상담 + 오늘 상담을 연결해 브리핑/이력/기록 확정을 시연합니다.
        -- 오늘 상담에는 준비 데이터를 넣지 않아 실제 AI 브리핑 생성을 시연할 수 있습니다.
        IF r.n = 1 THEN
            SELECT consultation_id INTO v_previous FROM consultations
            WHERE fc_customer_id = v_relation AND purpose = '기존 계약 확인 및 가족 보장 현황 파악'
            ORDER BY consultation_id LIMIT 1;
            IF v_previous IS NULL THEN
                INSERT INTO consultations (fc_customer_id, scheduled_at, purpose, consult_type, status,
                    memo, memo_updated_at, ai_record, confirmed_at, record_confirmed_at, created_at, updated_at)
                VALUES (v_relation, now() - INTERVAL '14 days', '기존 계약 확인 및 가족 보장 현황 파악',
                    'CONTRACT_REVIEW', 'completed',
                    '기존 종신보험은 유지 희망. 가족력으로 암 보장이 걱정된다고 함. 추가 예산 월 5만 원. 다음 상담에서 약관 기준으로 설명 예정.',
                    now() - INTERVAL '14 days',
                    jsonb_build_object('customerNeeds', '기존 계약 유지 및 암 보장 점검',
                        'explainedContent', '기존 계약 현황과 추가 보장 검토 절차 안내',
                        'customerQuestions', '기존 보험을 유지하면서 암 보장을 추가할 수 있나요?',
                        'unresolvedIssues', '현재 암 진단 보장 금액과 신규 가입 조건 확인 필요',
                        'nextActions', '다음 상담에서 상품 약관에 근거해 보장 범위와 가입 조건 설명'),
                    now() - INTERVAL '15 days', now() - INTERVAL '14 days',
                    now() - INTERVAL '15 days', now() - INTERVAL '14 days')
                RETURNING consultation_id INTO v_previous;
            END IF;
            INSERT INTO consultation_preparations (consultation_id, summary_briefing, created_at, updated_at)
            VALUES (v_previous, '기존 계약 유지 의사가 강하며 자녀를 포함한 가족 보장 점검을 희망합니다. 추가 예산과 암 보장 공백을 확인하세요.', now(), now())
            ON CONFLICT (consultation_id) DO NOTHING;
            SELECT preparation_id INTO STRICT v_preparation FROM consultation_preparations WHERE consultation_id = v_previous;
            INSERT INTO expected_questions (preparation_id, question, answer, created_at, updated_at)
            SELECT v_preparation, '현재 보험을 해지해야 하나요?',
                '기존 계약의 보장과 해지 시 불이익을 확인한 뒤 유지 여부를 함께 검토하겠습니다.', now(), now()
            WHERE NOT EXISTS (SELECT 1 FROM expected_questions WHERE preparation_id = v_preparation
                AND question = '현재 보험을 해지해야 하나요?');
        END IF;
    END LOOP;

    INSERT INTO todos (fc_id, content, due_date, is_completed, created_at, updated_at)
    SELECT v_fc, f.content, CURRENT_DATE + f.offset_days, f.completed, now(), now()
    FROM (VALUES
        ('김민준 고객 기존 계약 보장 내역 확인', 0, false),
        ('박지우 고객 첫 상담 준비 및 예산 확인', 0, false),
        ('이도윤 고객 보험료 미납 내역 확인', 1, false),
        ('최하은 고객 청구 구비 서류 확인 후 안내', 2, false),
        ('정서준 고객 상담 일정 재조율 연락', 7, false),
        ('김민준 고객 오늘 상담 일정 확인 연락', 0, true)
    ) AS f(content, offset_days, completed)
    WHERE NOT EXISTS (SELECT 1 FROM todos t WHERE t.fc_id = v_fc AND t.content = f.content);
END $$;

-- 실행 결과 및 API 시연에 사용할 ID 확인
SELECT u.login_id, c.customer_id, c.full_name, f.fc_customer_id,
       s.consultation_id, s.purpose, s.status, s.scheduled_at
FROM financial_consultants u
JOIN fc_customers f ON f.fc_id = u.fc_id
JOIN customers c ON c.customer_id = f.customer_id
LEFT JOIN consultations s ON s.fc_customer_id = f.fc_customer_id
WHERE u.login_id = 'demo_fc'
ORDER BY c.customer_id, s.scheduled_at;
COMMIT;
