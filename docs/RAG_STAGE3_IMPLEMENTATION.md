# 3단계: OpenAI 임베딩 및 PostgreSQL 적재

## 이번에 수정한 내용

| 변경 대상 | 수정 내용과 역할 |
| --- | --- |
| `client/rag/EmbeddingClient` | 여러 청크를 배치로 전송하고 타임아웃, 제한 재시도, 차원/응답 순서/유한값 검증을 담당한다. |
| `service/rag/DocumentIngestionService` | 메타데이터 탐색 → 기존 상품 매핑 → SHA-256 → 변경 여부 검사 → PDF 파싱/청킹 → 임베딩 → DB 교체를 조율한다. |
| `repository/rag/DocumentIngestionRepository` | 상품 조회, 문서 식별, 청크 수 집계, 문서별 잠금 및 원자적 청크 교체를 담당한다. 서비스에서 JPA와 트랜잭션 코드를 분리했다. |
| `controller/rag/DocumentController` | 로컬 적재 POST와 저장 목록 GET을 제공한다. Swagger 태그·API 설명·파라미터 설명과 단일 문서 경로 예시를 추가했다. |
| `dto/rag` | 적재 집계, 문서별 처리 결과, 저장 문서 목록을 타입이 있는 record로 분리하고 Swagger 필드 설명/예시를 추가했다. |
| `global/config/rag/RagConfiguration` | PDF 추출기와 청커를 Spring 빈으로 구성한다. 순수 PDF 처리 클래스는 Spring 없이 CLI에서도 사용한다. |
| `domain/insurance/entity/ProductDocument` | 청크 교체와 함께 해시·버전·적용일·모델·완료 상태를 갱신하는 `completeReplacement`를 추가했다. |
| 패키지 및 `build.gradle` | `domain`에는 엔티티와 enum만 남겼다. 컨트롤러/서비스/Repository/클라이언트/DTO/순수 PDF 코드를 루트의 각 패키지로 이동하고 테스트 import와 `ragDebug` 진입점을 갱신했다. |
| `application-local.yaml` | 배치 크기·타임아웃·재시도 설정과 원본 경로 주석을 추가했다. 현재 로컬 서버 포트는 8081이다. |
| 테스트 | 실제 HTTP 대신 로컬 서버로 오류를 재현하는 테스트, 실제 PostgreSQL 교체/롤백 테스트, 명시적으로 켜는 OpenAI 스모크 테스트를 추가했다. |

핵심 분기에는 왜 그렇게 처리하는지 주석을 보강했다. 서비스에 긴 트랜잭션을 두지 않는 이유, 없는 상품 ID를 우회하지 않는 이유, 파일 경로/변경 검사, 신규 문서까지 보호하는 DB 잠금, 응답 인덱스 복원, 실패 로그에서 원문을 제외하는 이유를 설명한다. 주석과 Swagger 설명 추가는 적재 동작 자체를 변경하지 않는다.

## 패키지 구조

`wonit.hackathon.a1` 기준이다. `domain`에는 엔티티와 enum만 둔다.

```text
controller/rag/DocumentController.java
service/rag/DocumentIngestionService.java
repository/rag/DocumentIngestionRepository.java
repository/rag/ProductDocumentRepository.java
repository/rag/ProductChunkRepository.java
client/rag/EmbeddingClient.java
dto/rag/{IngestionReport,IngestionResult,DocumentSummary}.java
rag/{PdfExtractor,ArticleChunker,RagDebug}.java
global/config/rag/RagConfiguration.java
domain/insurance/entity/{InsuranceProduct,ProductDocument,ProductChunk}.java
domain/insurance/enums/{DocumentType,IngestionStatus,PaymentFrequency}.java
```

서비스는 파일 탐색·메타데이터·해시·PDF 파싱·청킹·임베딩을 조율한다. DB 조회와 청크 교체 트랜잭션은 Repository가 담당한다. OpenAI 호출 중에는 DB 트랜잭션을 열지 않는다. `RagConfiguration`에서 추출기와 청커를 빈으로 구성한다. 기존 독립 CLI도 같은 순수 PDF 처리 클래스를 사용한다.

## 임베딩

- 고정 모델 `text-embedding-3-small`, 차원 1536, float 응답.
- `OPENAI_API_KEY`를 Spring 환경에서 읽으며 로컬에서는 기존 `.env` import를 사용할 수 있다.
- 기본 배치 16개, 요청 타임아웃 60초, 연결 타임아웃 10초.
- 429와 5xx만 최대 3회 재시도한다. 대기 시간은 1·2·4초이며 전송 오류와 타임아웃은 즉시 실패한다.
- 응답 수·인덱스 중복/범위·1536차원·숫자/유한값을 검증하고 요청 순서로 복원한다.
- HTTP 코드, 재시도 횟수, 문서 경로, 오류 종류만 로깅한다. API 키·본문·응답 원문·인증 헤더를 로그나 보고서에 남기지 않는다.
- 입력당 UTF-8 바이트 수를 8192 이하로 제한한다. 기본 청크 길이는 이 범위 안에 있다.

요청 규격 참고: [OpenAI Embeddings API](https://developers.openai.com/api/reference/resources/embeddings/methods/create).

## 식별 및 교체

문서는 상품 ID·문서 유형·저장 경로로 식별한다. SHA-256과 `COMPLETED` 상태, 임베딩 모델이 같으면 생략한다. `productId`가 명시되어 있으면 실제 존재하는 ID만 사용한다. ID가 비어 있으면 상품명이 정확히 한 상품과 일치할 때만 연결한다. 매핑 불가/중복 상품명은 `MAPPING_REQUIRED`로 보고하며 임의 상품을 생성하지 않는다.

모든 청크와 벡터를 준비하고 파일 해시를 다시 검사한 뒤 Repository에서 문서 식별자별 PostgreSQL advisory transaction lock을 획득한다. 기존 청크 삭제·새 청크 저장·문서 해시 및 완료 상태 갱신은 한 트랜잭션이다. 실패하면 기존 청크·해시·상태를 롤백한다. 신규 문서 실패는 DB에 불완전한 문서를 남기지 않고 응답에 보고한다. 변경 문서 실패도 기존 완료 데이터를 유지하고 이번 시도의 실패는 응답에서 확인한다.

텍스트 없는 PDF는 실패한다. 일부 이미지 전용 페이지가 있는 문서는 추출 가능한 텍스트를 적재하고 응답 `ocrPages`에 해당 실제 PDF 페이지를 표시한다. OCR과 표의 행·열 재구성은 수행하지 않는다.

## API 사용

`local` 프로필에서만 컨트롤러를 등록하며 `prod`, `production`이 함께 활성화되면 등록하지 않는다. POST는 직접 연결한 요청 주소가 loopback일 때만 허용한다. 프록시가 외부 요청을 loopback으로 전달하지 않도록 로컬 실행 시 서버도 `127.0.0.1`에 바인딩한다.

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local --server.address=127.0.0.1 --server.port=8081'
```

- `POST /api/admin/documents/ingest`: 전체 신규·변경 문서 처리.
- 선택 쿼리 `productId`: 매핑된 실제 상품 ID로 제한.
- 선택 쿼리 `documentPath`: `documents/` 기준 상대 경로(예: `product_01/상품요약서.pdf`)와 정확히 일치하는 문서로 제한. 한글은 URL 인코딩한다.
- `GET /api/documents`: 문서 ID·상품 ID·경로·상태·해시·청크 수.

응답의 `success`, `skipped`, `failed`는 처리 건수다. `failed`에는 매핑 필요 문서도 포함되며, metadata.json 자체를 읽을 수 없으면 해당 폴더를 실패 1건으로 집계하므로 항상 PDF 수와 같지는 않다. `processedChunks`는 이번에 저장한 청크 수, `totalChunks`는 DB 전체 청크 수다. 문서별 실패와 OCR 필요 페이지는 `documents`에서 확인한다. 일치하는 필터가 없으면 문서 수는 모두 0이다. 두 필터를 함께 입력하면 둘 다 일치해야 한다. `productId`는 매핑을 생성하는 값이 아니다.

## Swagger에서 API 테스트하기

### 1. 실행 전 준비

프로젝트 루트에서 실행한다. PostgreSQL과 기존 RAG 스키마가 준비되어 있어야 하며, 다음 값은 환경 변수 또는 Git에서 제외된 로컬 `.env`에 설정한다. 실제 비밀 값은 Swagger나 문서에 입력하지 않는다.

```dotenv
WONMATE_DB_URL=jdbc:postgresql://localhost:5432/wonmate
WONMATE_DB_USERNAME=<로컬 DB 사용자>
WONMATE_DB_PASSWORD=<로컬 DB 비밀번호>
OPENAI_API_KEY=<OpenAI API 키>
```

`documents/product_01/metadata.json`의 예시는 다음과 같다. 아래 `10`은 설명용 ID이며, 반드시 실제 기존 상품 ID로 바꾸거나 `null`로 두고 고유한 상품명으로 매핑해야 한다. `insurerName`은 현재 매핑 조건으로 사용하지 않는다.

```json
{
  "productId": 10,
  "productName": "무배당엔젤쑥쑥어린이보장보험",
  "insurerName": "동양생명",
  "documents": [
    {
      "fileName": "상품요약서_무배당엔젤쑥쑥어린이보장보험_20260901_00.pdf",
      "documentType": "SUMMARY",
      "version": null,
      "effectiveDate": null
    }
  ]
}
```

문서 유형은 `TERMS` 또는 `SUMMARY`다. 적용일을 확인했다면 `effectiveDate`는 `2026-09-01` 같은 ISO 날짜로 입력한다. 파일명에 날짜가 있다고 자동으로 적용일을 추정하지 않는다. 명시한 `productId`가 DB에 없으면 상품명이 일치하더라도 우회 매핑하지 않는다.

### 2. Swagger 열기

위 `bootRun` 명령으로 실행한 뒤 다음 주소로 접속한다.

- Swagger UI: `http://127.0.0.1:8081/swagger-ui/index.html`
- OpenAPI JSON: `http://127.0.0.1:8081/v3/api-docs`
- API 태그: **RAG 문서 적재**

현재 `application.yaml`은 `local`을 기본 활성 프로필로 지정하고 있지만, 재현용 명령은 프로필을 명시한다. `local`이 아니거나 `prod`/`production`이 함께 활성화되면 이 컨트롤러의 POST와 GET 모두 등록되지 않는다. API 키는 서버에서 읽으므로 Swagger의 Authorization 입력은 필요 없다.

### 3. 단일 문서를 먼저 적재

1. `POST /api/admin/documents/ingest`를 펼치고 **Try it out**을 누른다.
2. `productId`는 처음에는 비운다. 값이 없거나 잘못된 상품 필터로 0건이 나오는 상황을 피하고 문서의 매핑 결과부터 확인한다.
3. `documentPath`에 다음 값을 한글 원문으로 입력한다. `documents/` 접두사는 붙이지 않는다. Swagger가 URL 인코딩한다.
4. 요청 본문은 입력하지 않고 **Execute**를 누른다. 완료될 때까지 처리하는 동기 API이며 긴 PDF는 시간이 걸린다.

```text
product_01/상품요약서_무배당엔젤쑥쑥어린이보장보험_20260901_00.pdf
```

아래는 상품 매핑이 준비되고 다른 저장 문서가 없다는 가정의 **성공 예시**다. 현재 영구 적재 성공 기록이 아니다. 청크 수는 원본 PDF와 설정에 따라 달라진다.

```json
{
  "success": 1,
  "skipped": 0,
  "failed": 0,
  "processedChunks": 17,
  "totalChunks": 17,
  "documents": [
    {
      "path": "product_01/상품요약서_무배당엔젤쑥쑥어린이보장보험_20260901_00.pdf",
      "status": "COMPLETED",
      "chunks": 17,
      "reason": null,
      "ocrPages": []
    }
  ]
}
```

매핑할 상품이 없는 경우에는 HTTP 200이어도 다음처럼 처리 실패가 담긴다. HTTP 상태만 보고 적재 성공으로 판단하지 말고 `failed`와 문서별 `status`를 확인한다.

```json
{
  "success": 0,
  "skipped": 0,
  "failed": 1,
  "processedChunks": 0,
  "totalChunks": 0,
  "documents": [
    {
      "path": "product_01/상품요약서_무배당엔젤쑥쑥어린이보장보험_20260901_00.pdf",
      "status": "MAPPING_REQUIRED",
      "chunks": 0,
      "reason": "No unique existing product mapping",
      "ocrPages": []
    }
  ]
}
```

### 4. 저장된 문서 조회

`GET /api/documents`에서 **Try it out → Execute**를 누른다. 본문과 쿼리는 없다. 다음 ID와 해시는 형식을 설명하기 위한 가상 값이다.

```json
[
  {
    "documentId": 101,
    "productId": 10,
    "path": "documents/product_01/상품요약서_무배당엔젤쑥쑥어린이보장보험_20260901_00.pdf",
    "status": "COMPLETED",
    "hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "chunkCount": 17
  }
]
```

GET의 `path`는 저장 경로라 기본적으로 `documents/`로 시작한다. POST 필터와 문서별 결과의 `path`는 그 접두사가 없는 상대 경로다. 빈 DB는 `[]`를 반환한다. 실패한 신규 문서는 저장하지 않으므로 GET에 나타나지 않는다. 변경 문서의 적재가 실패하면 GET에는 기존 `COMPLETED` 상태와 기존 해시·청크 수가 그대로 보인다.

### 5. 중복·변경·실패 테스트

| 순서 | Swagger에서 할 일 | 확인할 결과 |
| --- | --- | --- |
| 동일 문서 재요청 | 성공한 단일 문서의 POST를 그대로 다시 실행 | `skipped=1`, `processedChunks=0`, 문서 상태 `SKIPPED`. GET의 문서 ID·해시·청크 수 유지. 임베딩을 다시 호출하지 않음. |
| 변경 문서 처리 | 원본을 백업한 뒤 같은 경로의 PDF를 다른 유효한 내용으로 바꾸고 같은 POST 실행 | `success=1`, 새로운 해시, 같은 문서 ID. 청크 수는 내용에 따라 같을 수도 있음. 기존 청크에 덧붙이지 않고 교체. |
| 파싱 실패 시 보존 | 변경 테스트와 별도로 원본을 백업하고 해당 경로를 손상된 PDF로 교체한 뒤 같은 POST 실행. 확인 후 원본 복원 | `failed=1`, `status=FAILED`, `chunks=0`. GET의 이전 완료 해시·청크 수 유지. 파싱 단계 실패이므로 임베딩 호출 없음. |
| 매핑 필요 | 매핑되지 않은 문서를 `documentPath`만으로 선택 | `failed=1`, `MAPPING_REQUIRED`. 문서/청크 저장 없음. |
| 상품 필터 | 실제 매핑된 `productId`를 입력하고 `documentPath`를 비움 | 해당 상품의 문서만 처리. ID가 틀리면 일치 결과 0건일 수 있음. |
| 일괄 적재 | 단일 문서 성공과 재요청 생략을 확인한 후 두 파라미터를 모두 비우고 실행 | 기존 완료 문서는 생략, 신규/변경 문서는 저장. 일부 실패해도 다른 완료 결과 보존. |

파일명이나 경로를 바꾸면 문서 식별자가 달라져 **새 문서**가 된다. 변경 문서 테스트는 같은 경로에서 PDF 바이너리 내용을 바꿔야 한다. 메타데이터의 버전/적용일만 바꾸고 PDF 해시가 같다면 현재 구현은 기존 완료 문서를 생략한다.

API 오류나 DB 교체 도중 실패까지 재현하는 검증은 아래 자동 테스트를 사용한다. Swagger에서 무작정 같은 요청을 재시도하거나 일괄 적재부터 실행하지 않는다. 문서별 `reason`에는 `IllegalStateException`, `IOException` 같은 오류 종류만 표시될 수 있으며 상세 HTTP 코드는 서버의 안전한 로그에서 확인한다.

### 6. PowerShell로 같은 요청 보내기

Swagger와 같은 API다. 아래 예시는 환경 변수나 키를 출력하지 않는다.

```powershell
$baseUrl = 'http://127.0.0.1:8081'
$document = 'product_01/상품요약서_무배당엔젤쑥쑥어린이보장보험_20260901_00.pdf'
$requestUrl = $baseUrl + '/api/admin/documents/ingest?documentPath=' + [Uri]::EscapeDataString($document)
Invoke-RestMethod -Method Post -Uri $requestUrl -TimeoutSec 600
Invoke-RestMethod -Method Get -Uri ($baseUrl + '/api/documents')

# 단일 문서 검증이 끝난 뒤에만 전체 적재 실행
Invoke-RestMethod -Method Post -Uri ($baseUrl + '/api/admin/documents/ingest') -TimeoutSec 3600
```

## DB에서 저장 결과 확인

Swagger GET은 벡터 본문을 노출하지 않는다. 연결한 기존 DB에서 아래 SQL로 차원과 관계를 확인한다. WHERE 조건에는 조회 API가 반환한 실제 문서 ID를 사용한다.

```sql
-- 문서별 저장 상태와 청크/임베딩 수
SELECT d.document_id, d.product_id, d.ingestion_status, d.file_hash,
       count(c.chunk_id) AS chunk_count,
       count(c.embedding) AS embedded_count
FROM product_documents d
LEFT JOIN product_chunks c ON c.document_id = d.document_id
GROUP BY d.document_id, d.product_id, d.ingestion_status, d.file_hash
ORDER BY d.document_id;

-- 저장된 벡터의 차원: 정상 적재 데이터는 dimensions=1536
SELECT vector_dims(embedding) AS dimensions, count(*)
FROM product_chunks
GROUP BY vector_dims(embedding);

-- 상품/문서 FK가 끊긴 데이터가 없는지 확인: 각각 0건
SELECT count(*) AS documents_without_product
FROM product_documents d
LEFT JOIN insurance_products p ON p.product_id = d.product_id
WHERE p.product_id IS NULL;

SELECT count(*) AS chunks_without_document
FROM product_chunks c
LEFT JOIN product_documents d ON d.document_id = c.document_id
WHERE d.document_id IS NULL;
```

## 자동 테스트 실행 예시

```powershell
# HTTP 재시도/타임아웃/검증 테스트: DB와 유료 API 불필요
.\gradlew.bat test --tests '*EmbeddingClientTests'

# DB 통합 테스트: WONMATE_DB_URL/USERNAME/PASSWORD가 프로세스 환경 변수에 필요
.\gradlew.bat test --tests '*DocumentIngestionPostgresTests' --tests '*RagPostgresTests'

# 전체 테스트: OpenAI 스모크는 명시적으로 켜지 않으면 생략
.\gradlew.bat test
```

`.env`는 Spring 설정으로 읽지만 JUnit의 `EnabledIfEnvironmentVariable` 조건은 프로세스 환경 변수만 확인한다. DB 통합 테스트가 skip되었다면 성공 실행과 구분한다. 통합 테스트의 임시 상품은 검증용으로만 생성하며 `finally`에서 연결된 청크·문서·상품을 정리한다. ID 시퀀스 값은 소비될 수 있다.

유료 API 스모크를 다시 실행하려면 `OPENAI_API_KEY`를 프로세스 환경 변수로 준비한 뒤 다음 명령을 실행한다. 실제 PDF의 청크 2개를 보내는 테스트이며 전체 PDF의 영구 적재 테스트는 아니다.

```powershell
$env:RAG_OPENAI_SMOKE = 'true'
try {
    .\gradlew.bat test --tests '*RealEmbeddingSmokeTests' --rerun-tasks
} finally {
    Remove-Item Env:RAG_OPENAI_SMOKE
}
```

## 검증 및 실제 적재 제한

2026-10-27 로컬 PostgreSQL과 OpenAI를 사용해 검증했다.

- 실제 `product_01` 요약서 PDF의 청크 2개를 OpenAI에 배치 전송하여 1536차원 응답을 확인했다. 결과는 `build/rag-ingest/openai-smoke.json`에 저장했다.
- HTTP 테스트: 배치 분할, 429/500 제한 재시도, 401 즉시 실패, 잘못된 차원 거부, 타임아웃.
- PostgreSQL 통합 테스트: 임시 상품·PDF와 테스트 임베딩으로 저장, 동일 문서 생략, 변경 문서 교체, API 실패 및 교체 도중 검증 실패 시 기존 청크/해시/상태 보존, 없는 상품 매핑 거부를 검증했다. 임시 상품과 문서·청크는 테스트 후 삭제한다. 기존 벡터 매핑 테스트는 1536차원 벡터와 FK 왕복을 검증한다.
- OpenAI 실제 호출을 포함한 최초 전체 실행은 테스트 17개가 통과했다. 패키지 재배치 검증에서는 유료 API 재호출 없이 16개 통과, 실제 API 스모크 1개 생략을 기준으로 한다.
- 주석·Swagger 설명 추가 후에도 DB 통합 테스트를 포함해 16개 통과, 실제 API 스모크 1개 생략을 확인했다. 검증용 로컬 포트 18081에서 Swagger UI의 HTTP 200, OpenAPI의 두 엔드포인트, POST 파라미터 2개, 응답 필드 설명과 처리 상태 4개를 확인했다. 생성 결과는 `build/rag-ingest/openapi.json`에 저장했다. 문서의 사용자 실행 예시는 설정 포트 8081을 사용한다.

현재 `insurance_products`가 0건이므로 실제 PDF의 영구 적재는 완료할 수 없다. 전체 20개 문서가 `MAPPING_REQUIRED`다. 실제 적재 결과는 성공 0, 생략 0, 실패/매핑 필요 20, 저장 청크 0이며 `build/rag-ingest/mapping-report.json`에 기록했다. 단일 실제 문서의 DB 적재가 완료되지 않아 전체 임베딩·적재는 진행하지 않았다. 기존 상품 데이터를 준비한 뒤 단일 문서, 같은 문서 재요청, 전체 문서 순서로 실행해야 한다.

DB 통합 테스트는 `WONMATE_DB_URL`이 프로세스 환경 변수에 있어야 실행한다. 실제 OpenAI 테스트는 추가로 `RAG_OPENAI_SMOKE=true`, `OPENAI_API_KEY` 프로세스 환경 변수가 필요하다. 비밀 값은 출력하지 않는다.

검색·질문 임베딩·GPT 답변은 구현하지 않았다.
