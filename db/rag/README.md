# RAG 도메인 DB 설정 (1단계)

## 확인한 프로젝트 상태

- Java 21, Gradle 9.8.2, 실제 Spring Boot **4.1.2**, Hibernate **7.4.13.Final**. 요청의 Boot 3.x와 달라 기존 버전을 유지했다.
- `InsuranceProduct`의 PK는 `Long id`, DB 컬럼은 `insurance_products.product_id` (`bigint`).
- `ProductDocument`, `ProductChunk`가 이미 일부 존재하여 확장했다. 기존 스타일대로 Java PK 이름 `id`, 연관 필드 `insuranceProduct`, `productDocument`를 유지했다. 부모 컬렉션 없이 자식의 LAZY ManyToOne 및 Repository로 1:N을 표현한다. cascade 삭제는 없다.
- 기존 Repository, DBML 파일, SQL 마이그레이션 도구 및 DB 연결 설정은 발견되지 않았다. Flyway 자동 실행을 도입하지 않고 명시적으로 실행하는 SQL을 제공한다.
- 문서 enum은 metadata.json과 같은 `TERMS`, `SUMMARY`이다. 두 새 enum은 `varchar` + CHECK 제약으로 저장한다. 다른 기존 도메인의 PostgreSQL named enum 매핑은 유지한다.
- `createdAt`, `updatedAt`은 기존 `BaseEntity`와 활성화된 JPA auditing을 재사용한다. SQL 직접 UPDATE 시 `updated_at`은 실행자가 갱신해야 한다.
- 현재 단계에 불필요한 Spring AI VectorStore starter와 해당 BOM을 제거했다. API 호출이나 별도 `vector_store` 테이블 자동 생성을 요구하지 않는다. 벡터는 기존 `hibernate-vector` 의존성으로 매핑한다.
- 전체 빌드에 막혔던 기존 `CustomerWiki`의 누락된 `BaseTimeEntity` 참조를 기존 `BaseEntity`로 수정했다.

## 로컬 접속 및 실행

PowerShell에서 접속 정보를 세션 환경 변수로 설정한다. 비밀번호는 코드에 저장하지 않는다. 로컬 프로필은 `optional:file:.env[.properties]`를 명시적으로 import하므로 프로젝트 루트의 `.env`도 읽는다. 환경 변수가 파일 값보다 우선한다.

```powershell
$env:WONMATE_DB_URL = 'jdbc:postgresql://localhost:5432/wonmate'
$env:WONMATE_DB_USERNAME = 'your_user'
# WONMATE_DB_PASSWORD는 로컬 실행 환경에서 설정
```

기존 업무 테이블과 `public.insurance_products(product_id bigint)`가 준비된 DB를 대상으로 실행한다. 빈 DB의 전체 업무 스키마를 이 SQL이 생성하지는 않는다. `psql`은 필요 시 비밀번호를 대화형으로 입력받는다.

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -h localhost -U your_user -d wonmate -v ON_ERROR_STOP=1 -f db/rag/01-create-rag-tables.sql
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -h localhost -U your_user -d wonmate -v ON_ERROR_STOP=1 -f db/rag/02-verify-rag.sql
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

`application-local.yaml`은 `ddl-auto: validate`, SQL 자동 초기화 비활성화다. 테이블이 없거나 기존 도메인 스키마가 다르면 시작이 실패하므로 해당 차이를 확인한다. `create`, `create-drop`으로 운영 데이터를 초기화하지 않는다.

생성 SQL은 트랜잭션 안에서 실행한다. RAG 테이블이 이미 있으면 오류로 중단하고 롤백한다. 기존 테이블에 `CREATE TABLE IF NOT EXISTS`로 성공을 가장하거나 데이터를 삭제하지 않는다. 이미 이전 버전 테이블이 있다면 실제 스키마/데이터를 확인한 별도 변경 SQL이 필요하다. 이전 `storage_uri` → `storage_url`, `page_number` → `start_page/end_page`, 소문자 enum → 대문자 문자열 변경과 기존 파일 해시의 실제 계산·보충을 검토해야 하며, 임의 기본 해시나 페이지를 채워 넣으면 안 된다.

## pgvector

로컬 PostgreSQL 18 서비스 실행과 `share/extension/vector.control`, `vector--0.8.1.sql`, `lib/vector.dll` 파일을 확인했다. 설치 파일 존재와 개별 DB 확장 활성화는 별개다. 생성 SQL의 `CREATE EXTENSION IF NOT EXISTS vector`는 대상 DB에서 권한이 있는 계정으로 실행해야 한다.

다른 환경에 확장이 없다면 [pgvector 공식 Windows 설치 절차](https://github.com/pgvector/pgvector#windows)에 따라 PostgreSQL 버전과 일치하는 x64 도구로 설치한다. Visual Studio C++ 지원과 관리자 권한의 x64 Native Tools 명령 프롬프트에서 다음 형태로 빌드한다.

```bat
set "PGROOT=C:\Program Files\PostgreSQL\18"
git clone https://github.com/pgvector/pgvector.git
cd pgvector
nmake /F Makefile.win
nmake /F Makefile.win install
```

재현 가능한 설치를 위해 검토한 릴리스 태그를 체크아웃한 후 빌드한다. 설치 후 DB마다 확장을 활성화하고 검증 SQL을 실행한다.

[Hibernate 7.4 공식 Vector 문서](https://docs.hibernate.org/orm/7.4/userguide/html_single/#vector)는 `hibernate-vector`, `float[]`, `@JdbcTypeCode(SqlTypes.VECTOR)`, `@Array(length=1536)` 조합을 지원한다. `columnDefinition="vector(1536)"`도 명시했다. 일반 float 배열 컬럼이나 JSON으로 대체하지 않았다. 벡터 저장·검색 서비스를 구현하지 않았다.

## 검증 상태 및 재실행

- `gradlew.bat build --offline`로 컴파일·패키징 및 DB 없는 Hibernate 매핑/Repository 파생 쿼리 생성 검사를 실행한다.
- `WONMATE_DB_URL`이 없으면 기존 context 테스트와 `RagPostgresTests`는 명시적으로 skip한다. 이는 실제 DB 테스트 성공을 의미하지 않는다.
- 접속 환경 변수 3개가 모두 설정되고 스키마가 준비되면 `gradlew.bat test --tests '*RagPostgresTests' --rerun-tasks`로 실제 저장/조회, 관계, auditing, nullable 벡터 및 1536차원 값의 왕복, 문서 내 청크 번호 중복 거부, 실제 DB 타입·확장 확인을 수행한다. 테스트 데이터는 트랜잭션 롤백된다. ID 시퀀스 값은 소비될 수 있으므로 테스트 DB 사용을 권장한다.
- SQL 검증 결과에서 두 FK, `(document_id, chunk_index)` UNIQUE 및 `embedding = vector(1536)`을 확인한다. 페이지는 1부터, chunkIndex는 0부터 시작한다.
- 2026-10-27 추가된 `.env` 설정으로 로컬 `wonmate` DB에 접속했다. PostgreSQL 18.6, pgvector 0.8.1을 확인하고 `CREATE EXTENSION IF NOT EXISTS vector`를 실행했다.
- 처음 접속한 DB에는 업무 테이블이 없었다. 기존 엔티티가 지정한 enum 타입도 없어 `00-existing-domain-enums.sql`로 누락 타입만 생성했다. 이후 사용자가 설정한 `ddl-auto: update`로 업무 테이블과 RAG 테이블을 생성했다. `01-create-rag-tables.sql`은 이번 DB에는 실행하지 않았다. 데이터 삭제/초기화는 수행하지 않았다.
- 실제 DB 대상 전체 빌드와 테스트 5개(매핑 1, context 1, PostgreSQL 3)가 통과했다. `02-verify-rag.sql`도 실행해 두 FK, UNIQUE 및 nullable `vector(1536)`을 확인했다. 통합 테스트의 임시 레코드는 롤백했다.
- 생성 이후 로컬 설정을 `ddl-auto: validate`, `generate-ddl: false`로 전환했다. 이 설정은 스키마를 검증하며 변경하지 않는다. Hibernate로 생성한 타임스탬프 컬럼은 nullable이며, JPA auditing이 저장 시 값을 채운다. 수동 생성 SQL의 NOT NULL/DEFAULT 정책과 이 부분은 다르다.

PDF 원본과 metadata.json을 변경하거나 적재하지 않았다. PDF 파싱, 청킹, 임베딩 API, 벡터 유사도 검색 및 GPT API는 구현하지 않았다.
