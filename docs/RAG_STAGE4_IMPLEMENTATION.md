# 4단계: pgvector 정확 검색

`POST /api/rag/search`는 질문을 기존 `EmbeddingClient`(text-embedding-3-small, 1536차원)로 임베딩한 뒤 PostgreSQL `<=>` 코사인 거리로 정렬한다. GPT 호출과 HNSW 생성은 없다.

```json
{
  "question": "암 진단비 지급 조건",
  "productIds": [1],
  "documentTypes": ["TERMS"],
  "topK": 5
}
```

응답은 `SearchResult` 배열이다. 각 항목은 `chunkId`, `documentId`, `productId`, `documentName`, `documentType`, `sectionTitle`, `sectionPath`, `startPage`, `endPage`, `content`, `similarityScore`를 포함한다. 일치하는 청크가 없으면 `[]`다. 문서 이름과 문서 ID로 원본 PDF를 식별하고 1부터 시작하는 실제 PDF 페이지에서 원문을 대조한다. 인쇄된 쪽 번호와 PDF 페이지 번호는 다를 수 있다.

- 질문은 공백만 입력할 수 없으며 최대 2000자다.
- `productIds`는 양의 ID 최대 100개, `documentTypes`는 TERMS/SUMMARY 최대 10개다. 둘 다 생략/null/빈 배열이면 해당 필터를 적용하지 않는다.
- `topK`는 생략/null이면 5, 허용 범위는 1~50이다.
- 필터는 SQL WHERE에서 적용한다. Java에서 다른 상품을 제거하지 않는다.
- 완료 상태이며 동일 임베딩 모델을 사용한 문서의 null/영벡터가 아닌 청크만 검색한다.
- 후보 CTE를 MATERIALIZED로 평가한 뒤 정확 거리를 계산한다. 기존 벡터 인덱스 유무에 의존하지 않으며 동점은 chunkId 순이다.
- 점수는 `1 - 코사인 거리`다. 일반적으로 -1~1이며 확률이나 답변 신뢰도가 아니다. 임의 점수 임계값이나 중복 제거로 결과를 변경하지 않는다.
- 외부 임베딩 HTTP 호출 중에는 DB 트랜잭션을 열지 않는다. 기존 타임아웃/재시도를 재사용한다.

로컬 스키마 설정을 `create`에서 `validate`로 수정했다. 기존 문서와 청크를 유지하며 누락된 스키마를 자동 생성하지 않는다.

## Swagger에서 검색 API 테스트하기

### 빈 DB에서 최초 적재

프로젝트 루트에서 다음 명령으로 메타데이터의 상품을 등록하고 실제 PDF를 청크·임베딩으로 저장할 수 있다. `.env`의 DB 접속 정보와 OpenAI 키를 사용하며 실제 임베딩 비용이 발생한다.

```powershell
.\gradlew.bat ragIngest
```

이 명령은 메타데이터에 명시된 ID를 검증하거나, 상품명이 정확히 일치하는 기존 상품을 재사용한다. 해당 상품이 없을 때만 생성한다. 보험료·가입 나이·납입 조건은 임의 생성하지 않으며 메타데이터로 확인되지 않은 보험 유형은 `UNKNOWN`으로 저장한다. `productId: null`인 메타데이터는 고유 상품명으로 매핑하므로 파일 수정이 필요 없다. 문서/청크는 기존 적재 서비스를 통해 생성하며 재실행하면 동일 해시·모델의 완료 문서를 생략한다.

실제 상품 ID는 `build/rag-ingest/product-mappings.json`, 완료/실패 집계는 `build/rag-ingest/ingestion-report.json`에서 확인한다. 검색 요청에는 이 매핑의 실제 ID를 사용한다. 문서 파일명만으로 상품 ID를 추정하지 않는다.

2026-10-27 최초 전체 적재 완료: 상품 10개, PDF 20개, 청크·임베딩 19,306개, 실패 0건. 최초 실행 결과는 `build/rag-ingest/initial-ingestion-report.json`에 보관했다. `product_01`의 실제 상품 ID는 6이며 Swagger에서 다음 요청으로 바로 검색할 수 있다.

```json
{
  "question": "암 진단비 지급 조건",
  "productIds": [6],
  "documentTypes": ["TERMS"],
  "topK": 5
}
```

### 1. 실행 및 데이터 확인

PostgreSQL의 기존 스키마와 임베딩이 저장된 청크가 필요하다. 프로젝트 루트의 `.env` 또는 환경 변수에 `WONMATE_DB_URL`, `WONMATE_DB_USERNAME`, `WONMATE_DB_PASSWORD`, `OPENAI_API_KEY`를 설정하고 실행한다.

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local --server.address=127.0.0.1 --server.port=8081'
```

브라우저에서 [Swagger UI](http://localhost:8081/swagger-ui/index.html)를 연다. `RAG 문서 적재`의 `GET /api/documents`를 실행해 실제 `productId`, `COMPLETED` 상태와 청크 수를 확인한다. 문서가 없거나 청크 수가 0이면 3단계 적재를 먼저 완료한다. 아래 예시의 상품 ID `1`은 실제 적재된 상품 ID로 바꾼다.

### 2. 상품별 약관 검색

`RAG 검색` → `POST /api/rag/search` → **Try it out**을 누른다. Request body에 다음 JSON을 입력하고 **Execute**를 누른다.

```json
{
  "question": "암 진단비 지급 조건",
  "productIds": [1],
  "documentTypes": ["TERMS"],
  "topK": 5
}
```

HTTP 200의 Response body에서 결과 배열을 확인한다. 아래는 필드 확인용 가상 응답이며 실제 검색 결과가 아니다.

```json
[
  {
    "chunkId": 101,
    "documentId": 10,
    "productId": 1,
    "documentName": "통합약관_예시.pdf",
    "documentType": "TERMS",
    "sectionTitle": "보험금의 지급사유",
    "sectionPath": "암진단특약 > 보험금의 지급사유",
    "startPage": 120,
    "endPage": 121,
    "content": "실제 응답에는 저장된 청크의 원문이 표시됩니다.",
    "similarityScore": 0.72
  }
]
```

결과는 최대 5건이며 점수가 높은 순서다. 모든 `productId`가 요청한 ID인지, `documentType`이 `TERMS`인지 확인한다. `sectionPath`에서 주계약/특약을 구분하고, 원본 PDF의 `startPage`~`endPage`를 열어 `content`를 직접 대조한다. `[]`이면 지정한 필터에 검색 가능한 청크가 있는지 확인한다.

### 3. 다섯 질문과 필터 비교

위 요청의 `question`만 아래 값으로 바꾸어 각각 실행한다. 각 응답을 저장해 관련성, 공통 조항 편중, 중복 청크, 주계약/특약 구분 및 원문·페이지 일치를 검토한다.

| 질문 | 중점 확인 |
| --- | --- |
| 암 진단비 지급 조건 | 진단 확정 요건과 해당 보장의 지급사유 |
| 면책기간 | 보장 개시일과 보장별 면책기간 |
| 암의 정의 | 암의 분류·제외 항목과 진단 확정 조항 |
| 입원 보장 | 입원의 정의와 해당 특약의 지급 조건 |
| 보험금 지급 제한 | 지급하지 않는 사유와 감액·제한 조건 |

상품 제한 없이 TERMS만 비교하려면 다음 요청을 사용한다. 이 경우 여러 상품이 반환될 수 있다.

```json
{
  "question": "암의 정의",
  "documentTypes": ["TERMS"],
  "topK": 5
}
```

같은 상품의 요약서와 비교하려면 최초 요청에서 `documentTypes`를 `["SUMMARY"]`로 바꾼다. 실제로 존재하는 다른 상품 ID로 `productIds`를 바꾸면 해당 상품의 문서만 나오는지도 확인한다. `topK`를 생략하면 기본값 5가 적용된다.

### 4. 입력 오류 확인

아래 요청은 `topK`가 허용 범위 밖이므로 HTTP 400이어야 한다. 질문을 공백으로 입력하거나 `documentTypes`에 `"INVALID"`를 넣어도 HTTP 400이다.

```json
{
  "question": "면책기간",
  "productIds": [1],
  "documentTypes": ["TERMS"],
  "topK": 0
}
```

정상 요청에서 HTTP 500이 발생하면 서버 로그에서 DB 연결·스키마 상태와 임베딩 API 설정·오류를 확인한다. API 키는 Swagger 요청 본문에 넣지 않는다.

## 실제 DB 적재 및 메타데이터 정리 결과 (2026-10-27)

### 변경사항

- `SearchController` → `ChunkSearchService` → `ChunkSearchRepository`로 질문 임베딩과 정확 벡터 검색을 구현했다. 상품·문서 유형 조건을 SQL에서 적용하고 원문과 출처를 반환한다.
- `ragIngest` Gradle 작업과 `RagIngest` 실행 클래스를 추가해 빈 DB에서도 메타데이터로 상품을 등록한 뒤 기존 적재 서비스를 실행할 수 있게 했다.
- 로컬 `ddl-auto`를 `create`에서 `validate`로 변경해 재시작 시 기존 적재 데이터를 유지한다.
- 현재 범위에서 사용하지 않는 문서 버전·적용일을 제거했다. 전체 10개 `metadata.json`의 `version`/`effectiveDate`, `ProductDocument`의 필드·생성 인자, 서비스의 메타데이터 파싱, Repository의 저장 인자, 테스트와 관련 문서 예시를 정리했다.
- 신규 DB 생성 SQL에서도 두 컬럼을 제거했다. 기존 DB 변경용 `db/rag/03-remove-document-version-fields.sql`을 추가하고 실제 로컬 DB에 적용했다. 테이블 재생성이나 PDF 재임베딩은 수행하지 않았다.

### 적재 및 보존 확인

| 확인 항목 | 결과 |
| --- | --- |
| 등록한 상품 | 10개 (`product_01`~`product_10`, 실제 ID 6~15) |
| 최초 적재 문서 | 20개 성공, 실패 0개 |
| 저장 청크 | 19,306개 |
| 임베딩이 있는 청크 | 19,306개 |
| 재실행 | 성공 0개, 생략 20개, 실패 0개, 새 청크 0개 |
| 문서 상태 | 20개 모두 `COMPLETED` |
| 두 컬럼 제거 후 | 문서 20개·청크 및 임베딩 19,306개 유지, 제거 대상 컬럼 0개 |

상품명은 실제 메타데이터를 사용했다. 확인되지 않은 보험료·가입 나이·납입 조건은 비워두었으며, 필수 보험 유형은 `UNKNOWN`으로 표시했다. 현재 엔티티에 보험사 필드가 없어 메타데이터의 `insurerName`을 새 DB 필드로 추가하지는 않았다.

최초 적재 보고서는 `build/rag-ingest/initial-ingestion-report.json`, 재실행 보고서는 `build/rag-ingest/ingestion-report.json`, 상품 매핑은 `build/rag-ingest/product-mappings.json`에 저장했다. `build/`와 `documents/`는 현재 Git 제외 대상이다. 로컬 PDF·메타데이터와 실행 보고서는 Git 커밋만으로 다른 환경에 전달되지 않으므로 별도로 준비해야 한다. DB에 적재한 레코드 역시 커밋에 포함되지 않는다.

### 검증

필드 제거 후 실제 PostgreSQL 통합 테스트를 포함한 전체 `test` 작업이 통과했다. 실제 임베딩 스모크와 전체 상품 검색 검토 테스트는 명시적으로 활성화할 때만 실행된다. 메타데이터 10개는 JSON 파싱을 확인했고, DB 조회로 컬럼 제거 및 기존 문서·벡터 개수 보존을 확인했다. 이미 실행 중인 서버는 변경한 엔티티·코드를 반영하도록 재시작해야 한다.

다른 기존 DB에도 반영할 때는 서버 코드를 갱신하고 동일한 컬럼 제거 SQL을 적용한다. 신규 DB에는 수정된 생성 SQL을 사용하므로 제거 SQL이 필수는 아니다.

## 검증과 현재 한계

실제 PostgreSQL 테스트에서 다른 상품과 SUMMARY에 더 가까운 벡터를 넣고도 TERMS/상품 필터와 LIMIT가 올바른지 확인했다. 코사인 점수, 원문/출처 전체 필드, null/영벡터, 미완료 문서와 다른 모델 제외, 없는 상품 결과도 검증한다. 테스트 레코드는 롤백한다. HTTP 테스트는 응답 필드와 잘못된 요청의 400 처리를 확인한다.

2026-10-27 최초 검증 당시 `product_chunks`는 0건이었으나, 이후 `ragIngest`로 메타데이터에 따른 상품 등록과 실제 PDF 임베딩·적재를 실행했다. 상품 ID는 `product_01`부터 `product_10` 순서로 6~15가 매핑됐다. 상품 ID가 1부터 시작한다고 가정하지 않는다.

실행 중인 HTTP 검색 API에서도 상품 6 / TERMS / topK 5로 다섯 질문을 호출하여 각각 5건이 반환되고 다른 상품이 섞이지 않는 것을 확인했다. 실제 원문과 페이지를 포함한 응답은 `build/rag-search/api-review.json`에 저장했다. `암의 정의`의 최상위 결과가 제자리암 정의이고 `입원 보장`의 최상위 결과가 다른 특약인 사례가 있어 관련성은 추가 검토가 필요하다. 원본 PDF 페이지 대조, 중복 및 공통 조항 편중에 대한 종합 검토까지 완료한 것은 아니다.

`RealSearchReviewTests`는 명시적으로 활성화하면 저장된 TERMS가 있는 상품마다 아래 다섯 질문을 검색하고 전체 원문·출처를 `build/rag-search/review.json`에 저장한다. 최초 실행한 이 테스트의 보고서는 당시 빈 DB 상태(`BLOCKED_NO_SEARCHABLE_TERMS`)를 기록한 과거 결과다. 적재 후 HTTP 검증 결과는 별도의 `api-review.json`을 확인한다. 데이터가 있으면 상품마다 유료 임베딩 5회가 발생한다.

```powershell
# DB 접속 변수와 OPENAI_API_KEY를 프로세스 환경에 설정한 뒤 실행
$env:RAG_SEARCH_REVIEW='true'
.\gradlew.bat test --tests '*RealSearchReviewTests' --rerun-tasks
```

검토 질문: 암 진단비 지급 조건 / 면책기간 / 암의 정의 / 입원 보장 / 보험금 지급 제한.

보고서의 각 결과를 원본 PDF 페이지에서 대조한다. `sectionPath`로 주계약과 특약을 구분하고, 일반 질문이 여러 보장을 동시에 반환하는지 확인한다. 상위 결과가 공통 조항으로 편중되면 청크 원문과 제목/경로의 구체성을 먼저 확인한다. 중복이 많으면 동일 문서의 겹친 청크인지 서로 다른 특약의 같은 문구인지 구분한다. 근거가 확보되기 전에는 재순위화나 검색 고도화를 추가하지 않는다.
