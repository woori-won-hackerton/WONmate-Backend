# WONmate 보험약관 RAG

Java 21, Gradle Wrapper, Spring Boot, PostgreSQL/pgvector 기반이다. 원본 PDF → 물리 페이지별 텍스트 → 조항 청크 → OpenAI 임베딩 → PostgreSQL → 상품별 검색 → 근거 기반 답변 순서로 처리한다.

모델 호출은 Spring AI의 ChatModel/EmbeddingModel을 사용하고, 저장·검색은 기존 SQL을 사용한다. 전환 내용과 정리한 코드는 [Spring AI 적용 문서](docs/RAG_SPRING_AI.md)를 참고한다.

설정은 `src/main/resources/application.yaml` 하나로 관리한다. 프로필을 생략하면 local을 사용하고 명시한 프로필은 우선 적용한다.

## 로컬 실행

프로젝트 루트에서 실행한다. PostgreSQL과 기존 업무 스키마가 필요하다. 빈 개발 DB에서는 `db/rag/00-existing-domain-enums.sql` → `db/bootstrap/01-create-business-tables.sql` → `db/rag/01-create-rag-tables.sql` → `db/rag/02-verify-rag.sql` 순서로 준비한다. 테이블은 SQL로만 생성하고 Hibernate는 검증만 한다. 운영 DB에 `create`/`create-drop`을 적용하지 않는다. 기존 스키마에는 `db/rag/02-verify-rag.sql`로 확인하고 필요한 마이그레이션만 적용한다. RAG 테이블이 없는 경우 `db/rag/01-create-rag-tables.sql`을 명시적으로 실행한다. 자세한 설치 절차는 [DB 설정](db/rag/README.md)을 참고한다.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
SELECT extversion FROM pg_extension WHERE extname='vector';
```

pgvector가 서버에 설치되어 있어야 한다. 저장 벡터는 `vector(1536)`이며 검색 모델은 `text-embedding-3-small`로 고정된다. 로컬 프로필은 스키마를 `validate`하며 자동 생성하지 않는다.

루트 `.env` 또는 프로세스 환경변수에 다음을 설정한다. `.env`는 Git에서 제외되며 프로세스 환경변수가 우선한다.

```dotenv
WONMATE_DB_URL=jdbc:postgresql://localhost:5432/wonmate
WONMATE_DB_USERNAME=your_user
WONMATE_DB_PASSWORD=your_password
OPENAI_API_KEY=your_key
OPENAI_CHAT_MODEL=gpt-6-luna
```

`OPENAI_CHAT_MODEL`은 실제 계정에서 사용 가능한 모델로 설정한다. 임베딩은 Spring AI를 통해 고정 모델 `text-embedding-3-small`을 호출한다. 사용하지 않던 `AI_EMBEDDING_MODEL` 설정은 제거했다. 배치 크기·타임아웃·재시도·청킹 설정은 `application.yaml`의 `rag` 항목에 있다.

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
# 로컬 포트: 8081
.\gradlew.bat test
```

## PDF와 메타데이터

```text
documents/
  product_01/
    metadata.json
    통합약관_상품명.pdf
    상품요약서_상품명.pdf
  unmatched/                 # 매핑되지 않은 자료, 적재 제외
```

```json
{
  "productId": null,
  "productName": "DB와 정확히 일치하는 정식 상품명",
  "insurerName": "보험사명",
  "documents": [
    {"fileName": "통합약관_상품명.pdf", "documentType": "TERMS"},
    {"fileName": "상품요약서_상품명.pdf", "documentType": "SUMMARY"}
  ]
}
```

ID가 null이면 DB에서 상품명이 정확히 하나 일치해야 한다. ID를 지정하면 존재 여부와 제공된 상품명을 검증한다. 상품 디렉터리 밖 경로는 허용하지 않는다. 페이지는 인쇄된 쪽수가 아닌 1부터 시작하는 PDF 물리 페이지다.

## 문서 적재

```powershell
# DB/API 없이 PDF 추출과 청킹만 검증
.\gradlew.bat ragDebug --args='--all'
# 메타데이터로 누락 상품 등록 후 전체 적재: 실제 임베딩 비용 발생
.\gradlew.bat ragIngest
Invoke-RestMethod 'http://localhost:8081/api/documents'
```

적재는 `ragIngest` CLI로만 수행한다. HTTP 적재 API와 단일 문서·상품 필터는 제거했다. 문서를 추가하거나 서버를 시작해도 자동으로 임베딩하지 않는다. 다른 컴퓨터에서의 준비·실행·재시도 절차는 [documents 적재 가이드](docs/RAG_LOCAL_LOAD_GUIDE.md)를 참고한다. CLI 보고서의 `failed`와 문서별 결과를 확인한다. 동일 해시·모델의 완료 문서는 건너뛴다. 변경 문서는 전체 임베딩 성공 후 짧은 DB 트랜잭션으로 교체하며, 실패하면 기존 문서·청크를 보존한다. 한 문서 실패 후 다른 문서를 계속 처리한다. PDF 삭제를 DB 삭제로 자동 전파하지 않는다.

## 검색·질문 API

`POST /api/rag/search`:

```json
{"question":"암 진단비 지급 조건", "productIds":[14], "documentTypes":["TERMS"], "topK":5}
```

ID는 예시이며 실제 목록에서 확인한다. 상품/문서 유형 생략 또는 빈 배열은 전체 검색이다. 결과는 원문, 상품·문서·청크 ID, 조항 제목/경로, 시작·끝 페이지, 코사인 유사도를 포함한다. 유사도는 정확도 확률이 아니다. `topK`는 1~50이다.

`POST /api/rag/ask`:

```json
{"questionType":"PRODUCT_EXPLANATION", "productId":14, "question":"주계약과 암 특약의 지급 조건을 구분해줘", "topK":5}
```

응답은 `answer`, `sources`다. `topK`는 검색 1회당 1~20이다. `PRODUCT_EXPLANATION`만 단일 `productId`를 필수로 받고 나머지 유형에서는 생략한다. `TERM_EXPLANATION`, `INSURANCE_CONCEPT`, `PRODUCT_EXPLANATION`의 세 가지 유형을 지원한다. 상세 요청·응답은 [API 예시](docs/RAG_API_EXAMPLES.md)를 참고한다.

## 실제 검증과 한계

2026-10-27 SQL 초기화·단일 YAML 정리 후 로컬 DB 회귀 테스트는 47개 중 40개 통과·7개 skip·실패 0개이며 실행 JAR 빌드도 성공했다. 전체 PDF 감사는 앞선 실행에서 통과했으며 이번 기능 삭제 후 재실행하지 않았다. 전체 감사 산출물은 20문서·13,934페이지·19,306청크에서 등록·매핑·해시·벡터·페이지 본문 대조 오류 0건이다. 반복 본문은 355그룹, 이미지 전용 OCR 후보는 17쪽이다. 실제 OpenAI 검색·답변 평가는 외부 전송 승인 대기로 미완료다. 검증 기록과 원본 확인 질문은 [7단계 결과](docs/RAG_STAGE7_VALIDATION.md)에 있다. 로컬 DB 테스트는 `WONMATE_DB_URL` 환경변수가 있어야 활성화된다. `.env`를 Spring이 읽는 것만으로 JUnit 환경변수 조건이 활성화되지는 않는다.

```powershell
$env:RAG_DATA_AUDIT='true'
.\gradlew.bat test --tests '*FullDataAuditTests'
$env:RAG_PDF_REVIEW='true'
.\gradlew.bat test --tests '*PdfEvidenceReviewTests'
# 다음 opt-in은 실제 유료 외부 호출을 발생시킨다.
$env:RAG_SEARCH_REVIEW='true'
.\gradlew.bat test --tests '*RealSearchReviewTests'
$env:RAG_ASK_LIVE='true'
.\gradlew.bat test --tests '*RealAskTests'
```

감사 결과는 `build/rag-audit/data.json`, 렌더링은 `build/rag-audit/pages/`, JUnit 결과는 `build/reports/tests/test/`에 생성된다. `build`는 Git 제외이므로 핵심 결과는 검증 문서에 별도 기록한다. 전체 PDF 감사는 수 분이 걸린다. 같은 작업 디렉터리에서 Gradle 테스트를 동시에 실행하면 결과 파일이 충돌할 수 있으므로 위 명령은 순서대로 실행한다.

OCR, 표 셀 관계 복원, 문장별 인용의 의미 검증은 미구현이다. 요약서는 약관 전체를 대체하지 않으며 주계약·특약, 감액·면책과 금액의 대응을 원문에서 확인해야 한다. 같은 문서 내 동일 본문과 상품 간 공통 조항은 감사 대상이며 서로 다른 출처를 무조건 삭제하지 않는다. 동일 청크 ID는 질문 컨텍스트에서 제거한다. 청킹 코드가 바뀌어도 PDF 해시가 같으면 자동 재청킹되지 않는다. Hybrid Search는 추가하지 않았다. 실제 검색 오류와 개선 전후 근거 없이 품질 향상을 주장하지 않는다.
