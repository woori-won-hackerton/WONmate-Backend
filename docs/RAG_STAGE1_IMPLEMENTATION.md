# 1단계: RAG 도메인 및 PostgreSQL 구축 구현 정리

## 1. 작업 범위

기존 보험상품과 연결되는 문서·청크 도메인을 확장하고, 로컬 PostgreSQL에서 저장·조회할 수 있도록 Entity, Repository, enum, 설정 및 SQL을 추가했다.

- 기존 `ProductDocument`, `ProductChunk`를 확장했다.
- Java 21, Spring Boot 4.1.2, Hibernate 7.4.13.Final을 기준으로 구현했다.
- 상품별 PDF와 `metadata.json`은 변경하거나 DB에 적재하지 않았다.
- PDF 파싱, 청킹, 임베딩 API, 벡터 유사도 검색, GPT API는 구현하지 않았다.

## 2. 도메인 구현

### ProductDocument

파일: `src/main/java/wonit/hackathon/a1/domain/insurance/entity/ProductDocument.java`

테이블: `product_documents`

| Java 필드 | DB 컬럼 | 역할 |
|---|---|---|
| id | document_id | Long PK, IDENTITY |
| insuranceProduct | product_id | InsuranceProduct와 LAZY ManyToOne |
| documentName | document_name | 원본 PDF 파일명 |
| documentType | document_type | TERMS / SUMMARY |
| storageUrl | storage_url | 프로젝트 루트 기준 로컬 PDF 상대 경로 |
| fileHash | file_hash | SHA-256, 64자리 16진수 검증 |
| documentVersion | document_version | 확인된 문서 버전, nullable |
| effectiveDate | effective_date | 확인된 적용일, nullable |
| ingestionStatus | ingestion_status | PENDING / PROCESSING / COMPLETED / FAILED |
| embeddingModel | embedding_model | 임베딩 모델명, nullable |
| createdAt / updatedAt | created_at / updated_at | BaseEntity의 JPA auditing 사용 |

신규 문서의 상태는 `PENDING`이다. 파일명 날짜는 적용일로 간주하지 않는다. 생성 메서드에 전달된 파일명과 경로를 저장하며, 현재 도메인 코드가 PDF를 열거나 파일 해시를 계산하지는 않는다.

기존 `storage_uri` 필드는 `storage_url`로 변경했다. Java PK 이름은 기존 스타일에 맞춰 `id`를 유지했다.

### ProductChunk

파일: `src/main/java/wonit/hackathon/a1/domain/insurance/entity/ProductChunk.java`

테이블: `product_chunks`

| Java 필드 | DB 컬럼 | 역할 |
|---|---|---|
| id | chunk_id | Long PK, IDENTITY |
| productDocument | document_id | ProductDocument와 LAZY ManyToOne |
| content | content | 원문 텍스트 |
| embedding | embedding | nullable vector(1536) |
| startPage / endPage | start_page / end_page | 시작·종료 페이지, 양끝 포함 |
| chunkIndex | chunk_index | 문서 안에서 0부터 시작하는 순서 |
| sectionTitle | section_title | 조항 제목, nullable |
| sectionPath | section_path | 상위 조항·특약 구분, nullable |

- 기존 `page_number`를 `start_page`, `end_page`로 확장했다.
- 페이지는 `1 <= startPage <= endPage`, 청크 번호는 0 이상이어야 한다.
- `(document_id, chunk_index)` UNIQUE 제약으로 같은 문서 내 번호 중복을 방지한다.
- 벡터는 1536차원과 유한값을 검증한다. 입력·반환 배열을 복사해 외부 변경을 차단한다.
- `hibernate-vector`, `SqlTypes.VECTOR`, `@Array(length = 1536)`으로 PostgreSQL 벡터 타입에 매핑한다.

상품 → 문서 → 청크 관계는 자식의 ManyToOne과 Repository 조회로 구현했다. 부모 컬렉션이나 cascade 삭제는 추가하지 않았다.

## 3. Repository 및 enum

패키지: `wonit.hackathon.a1.domain.insurance`

| 파일 | 구현 내용 |
|---|---|
| repository/ProductDocumentRepository.java | JpaRepository 기본 CRUD, 상품별 문서 ID 오름차순 조회 |
| repository/ProductChunkRepository.java | JpaRepository 기본 CRUD, 문서별 chunkIndex 오름차순 조회 |
| enums/DocumentType.java | 기존 소문자 값을 metadata.json과 같은 TERMS / SUMMARY로 변경 |
| enums/IngestionStatus.java | 문서 처리 상태 4개 추가 |

새 enum은 DB의 문자열 컬럼과 CHECK 제약으로 관리한다. 상태를 저장하는 메서드는 있지만 처리 워커나 상태 전이 규칙은 아직 구현하지 않았다.

## 4. PostgreSQL 구축 및 설정

파일: `src/main/resources/application-local.yaml`

- 로컬 프로필에서 프로젝트 루트의 `.env`를 명시적으로 import한다.
- 접속 정보는 `WONMATE_DB_URL`, `WONMATE_DB_USERNAME`, `WONMATE_DB_PASSWORD`를 사용한다.
- 환경 변수는 `.env`보다 우선한다. 접속 비밀번호는 문서와 소스에 기록하지 않는다.
- 현재 설정은 `ddl-auto: validate`, `generate-ddl: false`, SQL 자동 초기화 비활성화다.

로컬 `wonmate` DB에서 PostgreSQL 18.6과 pgvector 0.8.1을 확인했다. 최초 접속 시 테이블이 없는 상태였으며, 다음 순서로 구축했다.

1. `CREATE EXTENSION IF NOT EXISTS vector` 실행
2. 기존 도메인이 참조하는 PostgreSQL enum 타입 중 누락된 타입 생성
3. 당시 로컬 설정의 `ddl-auto: update`로 기존 업무 및 RAG 테이블 생성
4. 실제 DB 통합 테스트와 검증 SQL 실행
5. `ddl-auto: validate`로 전환 후 전체 빌드 재검증

기존 데이터를 삭제하거나 초기화하지 않았다. 테스트 임시 레코드는 롤백했으며 ID 시퀀스 값은 소비될 수 있다.

### SQL 파일

| 파일 | 용도 |
|---|---|
| db/rag/00-existing-domain-enums.sql | 기존 도메인이 지정한 이름의 PostgreSQL enum을 없을 때만 생성 |
| db/rag/01-create-rag-tables.sql | 기존 insurance_products가 준비된 DB에 RAG 테이블 생성 |
| db/rag/02-verify-rag.sql | 확장, 컬럼 타입, FK, UNIQUE 제약 확인 |
| db/rag/README.md | 접속·실행·검증 절차 및 기존 테이블 변경 시 주의점 |

현재 로컬 DB에는 Hibernate로 RAG 테이블을 생성했으므로 `01-create-rag-tables.sql`을 실행하지 않았다. 해당 SQL은 기존 RAG 테이블이 있으면 중단한다. Hibernate로 생성한 auditing 컬럼은 nullable이며, 수동 생성 SQL의 NOT NULL/DEFAULT 정책과 차이가 있다.

## 5. 테스트 내용 및 검증 결과

| 테스트 | 확인 내용 |
|---|---|
| A1ApplicationTests.contextLoads | 실제 DB 연결, Spring Boot 시작, 현재 validate 설정에 따른 스키마 검증 |
| RagMappingTests | DB 없이 Hibernate 벡터·관계·auditing 필드 매핑과 Repository 파생 쿼리 구성 |
| RagPostgresTests 저장·조회 | 상품·문서·청크 관계, 초기 상태, auditing 값 생성, 청크 조회 순서, 벡터·null 왕복 |
| RagPostgresTests 중복 제약 | 같은 문서의 중복 chunkIndex 저장을 DB가 거부하는지 확인 |
| RagPostgresTests 확장·타입 | vector 확장 활성화, 실제 nullable vector(1536) 타입 확인 |

DB 검증 당시 전체 5개 테스트가 실행되어 통과했고 Gradle 빌드도 성공했다. 별도 SQL로 두 FK와 UNIQUE 제약도 확인했다. auditing 검사는 생성 시 타임스탬프가 채워지는지 확인하며, 이후 수정 시각 변화까지 테스트하지는 않는다.

DB 테스트는 `WONMATE_DB_URL`이 **프로세스 환경 변수로 존재할 때** 실행한다. `.env`가 있어 애플리케이션 접속은 가능해도 이 JUnit 실행 조건이 자동으로 충족되지는 않는다. DB 환경 변수가 없으면 context 테스트 1개와 PostgreSQL 테스트 3개는 skip한다.

### 현재 변경 내역 기준 빌드 상태

주석·문서 정리 시점의 `build.gradle`에는 추가로 다음 변경이 존재한다.

- PostgreSQL JDBC 드라이버 `implementation` 버전 명시 추가. 기존 `runtimeOnly` 선언도 남아 있다.
- Swagger UI용 `springdoc-openapi-starter-webmvc-ui:3.1.1` 추가
- Spring AI VectorStore starter와 BOM 제거 후 `spring-ai-vector-store` 단독 의존성 추가

이번 정리 작업에서 실행한 `gradlew.bat build --offline`은 **실패**했다. 버전이 지정되지 않은 `org.springframework.ai:spring-ai-vector-store`를 해석하지 못해 `compileJava` 단계에서 중단됐다. 따라서 이전의 DB 테스트 5개 성공은 위 의존성 추가 전 검증 결과이며, 현재 변경 전체가 빌드에 성공했다고 볼 수 없다. 의존성은 이번 주석·문서 작업에서 변경하지 않았다.

## 6. 그 외 변경 및 주석 보강

- `CustomerWiki`의 존재하지 않는 `BaseTimeEntity` 참조를 프로젝트에 있는 `BaseEntity`로 수정했다.
- `.gitignore`에 `.env`와 `/documents/` 제외 설정이 있다. 로컬 PDF·metadata는 보관하지만 현재 Git 변경에는 포함되지 않는다.
- 문서 경로, 실제 적용일 확인 기준, 기본 처리 상태에 주석을 추가했다.
- 벡터 차원·복사 정책, 페이지 및 청크 번호 기준에 주석을 추가했다.
- DB 테스트 실행 조건, 롤백 범위, 테스트용 가상 파일 경로·해시와 1차 캐시 제거 이유를 설명했다.
- 현재 스테이징 여부를 변경하거나 실제 Git 커밋을 생성하지 않았다.

## 7. 커밋 메시지 초안

현재 전체 변경 범위를 기준으로 작성했다. 위 의존성 해석 문제를 해결하고 빌드를 재검증한 뒤 사용하는 것을 기준으로 한다.

```text
feat: RAG 문서·청크 도메인 및 PostgreSQL 기반 구성 추가

- ProductDocument에 파일 해시, 문서 버전, 적용일, 처리 상태와 auditing 추가
- ProductChunk에 페이지 범위, 조항 정보 및 vector(1536) 매핑 반영
- 문서 유형·처리 상태 enum과 상품별 문서·문서별 청크 조회 Repository 추가
- pgvector 및 기존 enum 준비, RAG 테이블 생성·검증 SQL 작성
- .env 기반 로컬 DB 연결과 스키마 validate 설정 추가
- Hibernate 매핑 및 PostgreSQL 저장·조회·중복 제약 테스트 추가
- CustomerWiki의 공통 엔티티 참조 수정
- JDBC·Swagger UI·Spring AI 의존성 구성 변경
- 로컬 환경 파일·문서 폴더 제외 설정과 구현 주석·문서 보강
```
