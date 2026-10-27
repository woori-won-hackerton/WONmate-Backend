# 다른 컴퓨터에서 documents PDF를 임베딩·DB 적재하기

## 빠른 실행: DB 스키마가 이미 준비된 경우

프로젝트 루트에 원본 `documents/` 폴더와 `.env`를 준비한 후 PowerShell에서 실행한다.

```powershell
java -version                         # Java 21
.\gradlew.bat ragIngest
if ($LASTEXITCODE -ne 0) { throw '적재 실패: build/rag-ingest/ingestion-report.json 확인' }
Get-Content -Raw -Encoding utf8 build/rag-ingest/ingestion-report.json
```

이 명령 하나로 메타데이터에 등록된 상품 확인/누락 상품 생성 → PDF 추출 → 청킹 → Spring AI 임베딩 → 문서·청크 저장을 수행한다. 서버를 별도로 실행할 필요가 없다. 임베딩은 OpenAI API를 사용하므로 원문 전송과 비용이 발생한다. 같은 PDF의 완료 데이터가 있으면 건너뛰어 재실행 비용을 줄인다.

서버를 시작하면 기본적으로 상품 매핑과 PDF 적재를 자동 실행한다. 완료 상태·파일 해시·임베딩 모델이 같은 문서는 API 호출 없이 건너뛰며, 새 문서와 변경·실패 문서는 처리한다. JWT나 로그인은 필요하지 않다. HTTP 적재·업로드 API와 파일 감시 기능은 없으므로 실행 중 추가한 파일은 다음 시작 시 처리한다.

```powershell
.\gradlew.bat bootRun
```

자동 적재는 서버 시작 과정에서 동기 실행되므로 첫 실행의 준비 완료까지 시간이 걸린다. 문서 적재가 실패해도 서버는 계속 실행하며, 보고서와 콘솔 경고를 확인해야 한다. 실패 문서는 다음 시작 때 다시 시도한다. `.env`에 `RAG_INGEST_ON_STARTUP=false`를 설정하면 자동 적재를 끌 수 있다. 독립 명령 `ragIngest`는 이 설정과 관계없이 한 번 실행한다.

## 1. 소스와 원본 복사

저장소를 받은 뒤 기존 컴퓨터의 `documents/` 전체를 프로젝트 루트에 복사한다. **`documents/`와 `.env`는 Git 제외 대상**이므로 clone만으로 생기지 않는다. PDF뿐 아니라 각 폴더의 `metadata.json`도 필요하다. 예전 `build/` 폴더는 옮길 필요가 없다.

```text
WONmate-Backend/
  gradlew.bat
  .env
  documents/
    product_01/
      metadata.json
      통합약관_상품명.pdf
      상품요약서_상품명.pdf
    product_02/...
```

Java 21과 PostgreSQL/pgvector를 준비한다. 별도 Gradle 설치는 필요 없다. 첫 실행은 Gradle과 의존성을 내려받으므로 네트워크가 필요하다. 새 컴퓨터에서는 `--offline`을 붙이지 않는다. macOS/Linux에서는 동일한 작업을 `./gradlew ragIngest`로 실행한다.

## 2. 환경변수 파일

프로젝트 루트 `.env`에 새 컴퓨터의 접속 정보를 직접 입력한다. 기존 컴퓨터의 DB 주소·계정·암호를 그대로 사용한다고 가정하지 않는다.

```dotenv
WONMATE_DB_URL=jdbc:postgresql://localhost:5432/wonmate
WONMATE_DB_USERNAME=your_user
WONMATE_DB_PASSWORD=your_password
OPENAI_API_KEY=your_key
OPENAI_CHAT_MODEL=gpt-6-luna
```

`ragIngest`가 local 프로필로 `.env`를 읽는다. 환경변수를 별도로 export할 필요는 없다. 단, 같은 이름의 프로세스 환경변수가 있으면 파일보다 우선하므로 다른 DB를 가리키는 오래된 값이 없는지 확인한다. 실제 계정에서 접근 가능한 답변 모델을 설정한다. 적재 작업 자체는 답변 생성 모델을 호출하지 않는다.

## 3. PostgreSQL 스키마 준비

### 기존 DB를 복원했거나 스키마가 있는 경우

접속 대상에 pgvector 확장이 활성화되어 있는지 확인한다. 설치·확장·SQL 상세 설명은 [DB 설정](../db/rag/README.md)을 참고한다.

```sql
SELECT extversion FROM pg_extension WHERE extname='vector';
SELECT to_regclass('public.insurance_products'),
       to_regclass('public.product_documents'),
       to_regclass('public.product_chunks');
```

모든 테이블이 있으면 `db/rag/02-verify-rag.sql`로 확인하고 바로 적재한다. 업무 테이블만 있고 RAG 테이블이 없으면 `db/rag/01-create-rag-tables.sql`을 실행한다. 이 파일은 이미 존재하는 RAG 테이블을 덮어쓰지 않으며, 재실행하면 오류가 난다.

### 완전히 비어 있는 개발 DB인 경우

PostgreSQL에 `wonmate` 개발 DB와 접속 계정을 먼저 준비하고 해당 DB에서 아래를 실행한다. pgvector가 서버에 설치되어 있어야 한다.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

신규 DB는 아래 SQL을 **명시된 순서대로 한 번만** 실행한다. 호스트·사용자·DB와 psql 실행 경로는 설치 환경에 맞춘다. `-W`는 암호를 대화형으로 입력받는다.

```powershell
# 1. 업무 enum → 2. 업무 테이블 → 3. RAG 테이블 → 4. 검증
psql -X -h localhost -U your_user -d wonmate -W -v ON_ERROR_STOP=1 -f db/rag/00-existing-domain-enums.sql
psql -X -h localhost -U your_user -d wonmate -W -v ON_ERROR_STOP=1 -f db/bootstrap/01-create-business-tables.sql
psql -X -h localhost -U your_user -d wonmate -W -v ON_ERROR_STOP=1 -f db/rag/01-create-rag-tables.sql
psql -X -h localhost -U your_user -d wonmate -W -v ON_ERROR_STOP=1 -f db/rag/02-verify-rag.sql
```

테이블 생성은 SQL만 사용한다. Hibernate는 `application.yaml`의 `ddl-auto: validate`, `generate-ddl: false`로 생성 결과를 검증하며 `update`로 실행하지 않는다. 기존 테이블이 있으면 생성 SQL은 실패하므로 복원 DB에 신규 설치 SQL을 일괄 재실행하지 않는다.

`db/rag/legacy/`는 기존 구버전 DB만을 위한 별도 변경 경로다. 신규 DB에서는 컬럼 제거 SQL을 실행하지 않는다. RAG 생성 SQL은 `document_version`, `effective_date`를 처음부터 포함하지 않으며 타임스탬프는 NOT NULL/DEFAULT 정책으로 생성한다.

설정은 `application.yaml` 하나만 사용한다. 프로필을 지정하지 않으면 `local`이 기본이고, 명시한 `SPRING_PROFILES_ACTIVE` 또는 실행 인자로 다른 프로필을 선택할 수 있다. 별도의 application-local.yaml은 없다.

## 4. 상품 매핑과 메타데이터

다른 DB에서는 상품 ID가 달라질 수 있다. 같은 정식 상품명으로 매핑하거나 생성하려면 메타데이터의 `productId`를 `null`로 둔다. 기존 ID를 명시했다면 대상 DB에 해당 ID와 정확한 상품명이 모두 일치해야 한다. 매핑 불일치 시 임의의 다른 상품으로 적재하지 않는다.

```json
{
  "productId": null,
  "productName": "무배당우리WON하는암보험",
  "insurerName": "동양생명",
  "documents": [
    {"fileName":"통합약관_무배당우리WON하는암보험_20260701.pdf","documentType":"TERMS"},
    {"fileName":"상품요약서_무배당우리WON하는암보험_20260701.pdf","documentType":"SUMMARY"}
  ]
}
```

파일명은 실제 PDF와 일치해야 한다. 폴더 밖 파일은 허용하지 않는다. `unmatched/`는 적재 대상에서 제외한다. 메타데이터에 등록하지 않은 PDF는 적재하지 않는다.

상품 분류는 메타데이터의 선택 필드 `insuranceType` (`CANCER`, `WHOLE_LIFE`, `CHILD`, `UNKNOWN`)을 사용한다. 필드가 없으면 버전 관리되는 `src/main/resources/rag/product-categories.json`의 정확한 상품명 매핑을 사용하며 미등록 상품은 `UNKNOWN`으로 둔다. 기존 상품의 null·빈 문자열·UNKNOWN 분류는 서버 시작/CLI 상품 매핑 때 보완하며 기존의 확정된 분류는 유지한다. 기존 DB 분류만 갱신하려면 `db/rag/legacy/06-classify-document-products.sql`을 실행할 수 있다. 분류 변경은 PDF 재임베딩을 요구하지 않는다.

`scripts/sql/mock-contracts.sql`은 문서 적재 후 실행하는 개발용 데이터다. 가짜 보험상품을 생성하지 않고 문서 기반 상품을 참조하며 FC·고객·계약은 목업으로 만든다. 문서 기반 10개 상품의 비어 있는 `base_premium`과 `payment_frequency`에 **임의의 테스트 값**을 채운다. 실제 보험료나 납입주기 자료가 아니며 기존에 있는 값은 덮어쓰지 않는다. 예전 목업 계약은 이 스크립트 전용 고객에 한해 실제 상품으로 연결하고, 예전 목업 상품 행 자체는 삭제하지 않는다. 문서 적재기는 보험료·가입 연령 등을 추정하지 않는다.

## 5. 적재·확인·재시도

예전 `[MOCK-CONTRACT]` 상품 4종을 제거하려면 문서 적재 후 `scripts/sql/remove-mock-products.sql`을 실행한다. 연결된 계약의 ID·보험료·날짜·상태를 유지한 채 문서 기반 상품으로 참조를 옮기고 목업 상품만 삭제한다. 대상 실제 상품이 없거나 목업 상품에 문서가 연결돼 있으면 전체 트랜잭션을 중단한다. 재실행 시 이미 삭제된 목업 상품은 건너뛴다.

추출 상태를 먼저 확인하고 싶으면 다음을 실행한다. 이 단계는 DB·OpenAI 호출 없이 로컬 PDF만 읽는다. 빠르게 적재하려면 이 단계를 생략하고 바로 `ragIngest`를 실행할 수 있다.

```powershell
.\gradlew.bat ragDebug --args='--all'
.\gradlew.bat ragIngest
```

결과 파일:

- `build/rag-ingest/product-mappings.json`: 실제 대상 DB의 상품 ID 매핑.
- `build/rag-ingest/ingestion-report.json`: 문서별 COMPLETED/SKIPPED/FAILED/MAPPING_REQUIRED, 처리 청크 수, DB 전체 청크 수.

`failed=0`인지 확인한다. 실패 문서가 있으면 보고서가 저장되고 명령은 실패로 종료한다. PDF/매핑/API 설정 문제를 수정한 뒤 **같은 명령을 다시 실행**한다. 완료된 동일 문서는 SKIPPED되며 실패한 문서는 다시 처리한다. 변경 PDF는 벡터를 모두 확보한 다음 문서 단위 트랜잭션으로 교체한다. 실패 때문에 기존 완료 데이터가 삭제되지 않는다. CLI 상품 매핑 사전 단계가 실패하면 적재 보고서가 생성되기 전에 종료될 수 있으므로 콘솔의 매핑 오류도 확인한다.

```sql
SELECT d.product_id, d.document_name, d.document_type, d.ingestion_status,
       count(c.chunk_id) AS chunks,
       count(c.embedding) AS embedded_chunks
FROM product_documents d LEFT JOIN product_chunks c USING(document_id)
GROUP BY d.document_id
ORDER BY d.product_id, d.document_type;
```

실제 새 DB의 상품 ID는 원래 PC와 같다고 가정하지 않는다. 적재 후 서버를 실행하면 `GET /api/documents`에서도 문서·상품 ID·해시·청크 수를 확인할 수 있다.

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
# 다른 터미널에서 조회
Invoke-RestMethod 'http://localhost:8081/api/documents'
```

## 모든 문서가 임베딩에 실패하는 경우

`DataIntegrityViolationException`이면 임베딩 이후 DB 제약 위반일 수 있다. 구버전 DB에 `storage_uri NOT NULL`이 남아 있고 현재 코드가 `storage_url`에 저장하는 경우, `db/rag/legacy/05-align-document-storage-path.sql`을 대상 DB에 한 번 실행한다. 기존 경로를 `storage_url`로 보완하고 예전 컬럼의 필수 제약을 해제하며 기존 문서·청크는 삭제하지 않는다.

`success=0 failed=20`이면 재실행에 앞서 보고서의 `documents[].reason`을 확인한다.
임베딩 오류는 외부 응답 본문이나 키를 기록하지 않고 다음처럼 구분한다.

- `Embedding HTTP 401`: 실제 적용되는 OpenAI API 키를 확인한다. 같은 이름의 프로세스 환경변수가 `.env`보다 우선한다.
- `Embedding HTTP 403`: 해당 프로젝트/키의 접근 권한을 확인한다.
- `Embedding HTTP 429`: 요청 제한 또는 사용 한도를 확인한다. 반복 실행보다 API 계정의 사용량·결제 상태를 먼저 확인한다.
- `Embedding HTTP 5xx`: 외부 서비스 오류다. 설정된 재시도 후에도 실패하면 나중에 다시 실행한다.
- `Embedding transport or response failure`: 네트워크·프록시·타임아웃 또는 응답 처리 문제를 추가 진단한다.
- `Invalid embedding index or dimension`: 임베딩 응답이 요구하는 1536차원 등의 검증을 통과하지 못했다.

원인을 수정한 뒤 프로젝트 루트에서 `./gradlew.bat ragIngest`를 다시 실행한다.
임베딩은 PDF에서 추출한 텍스트를 OpenAI로 전송하고 비용을 발생시킨다.
실패 문서의 청크 수 0은 텍스트 추출 실패를 확정하지 않는다. 벡터가 모두 준비되어야 문서 단위로 DB에 저장된다.

## 자주 막히는 부분

| 현상 | 확인할 내용 |
|---|---|
| documents 경로 오류 | 프로젝트 루트에서 실행했는지, Git에 없는 원본 폴더를 복사했는지 |
| DB 연결 실패 | PostgreSQL 실행·접속 주소·계정·암호·프로세스 환경변수 우선순위 |
| vector 타입 없음 | 서버에 pgvector 설치 후 대상 DB에서 CREATE EXTENSION 실행 |
| validate 또는 enum 오류 | 위 스키마 준비 절차와 00 SQL 확인 |
| 상품 매핑 오류 | 다른 PC의 고정 ID를 사용했는지, 상품명이 중복인지 |
| API 오류 | 키·권한·한도·네트워크 확인. 실패 문서는 재실행 가능 |
| 이미지 전용 PDF | OCR 미지원. 추출 가능한 텍스트가 없으면 적재 실패 |
| 청킹 코드를 바꿨는데 SKIPPED | 현재 캐시는 PDF 해시·모델 기준. 청킹 코드 변경만으로 강제 재처리하지 않음 |

임베딩 모델은 저장 벡터와 맞추기 위해 `text-embedding-3-small`, 1536차원으로 고정한다. 기본 배치 16과 재시도 3회를 사용하며 동시에 여러 적재 프로세스를 실행해 속도를 높이려 하지 않는다. 다른 컴퓨터에서 위 초기화 절차를 직접 실행한 결과까지 검증한 것은 아니며, 로컬 코드의 적재·롤백과 API 제거는 회귀 테스트로 확인한다.

SQL 초기화·설정 통합 후 로컬 DB 회귀 결과: `gradlew.bat cleanProcessResources test bootJar --offline` 성공, 총 47개 중 40개 통과·7개 skip·실패 0개. 임시 스키마에서 enum·업무·RAG SQL 생성 후 전체 엔티티 검증을 통과하고 DDL은 롤백했다. 실제 OpenAI 호출과 PDF 재임베딩은 실행하지 않았다.
