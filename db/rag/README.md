# PostgreSQL / pgvector 스키마 준비

현재 신규 DB는 SQL로만 생성한다. 애플리케이션은 단일 `application.yaml`의 `ddl-auto: validate`, `generate-ddl: false`, `spring.sql.init.mode: never`로 스키마를 검증하며 변경 SQL을 자동 실행하지 않는다. `ragIngest`도 validate를 사용한다.

## 빈 DB 초기화 순서

PostgreSQL 서버에 pgvector를 설치하고 접속할 개발 DB와 계정을 준비한다. 아래 명령의 호스트·계정·DB는 실제 환경에 맞춘다. psql 실행 파일이 PATH에 없으면 전체 경로를 사용한다.

```powershell
psql -X -h localhost -U your_user -d wonmate -W -v ON_ERROR_STOP=1 -c 'CREATE EXTENSION IF NOT EXISTS vector'
psql -X -h localhost -U your_user -d wonmate -W -v ON_ERROR_STOP=1 -f db/rag/00-existing-domain-enums.sql
psql -X -h localhost -U your_user -d wonmate -W -v ON_ERROR_STOP=1 -f db/bootstrap/01-create-business-tables.sql
psql -X -h localhost -U your_user -d wonmate -W -v ON_ERROR_STOP=1 -f db/rag/01-create-rag-tables.sql
psql -X -h localhost -U your_user -d wonmate -W -v ON_ERROR_STOP=1 -f db/rag/02-verify-rag.sql
```

| 파일 | 역할 | 실행 조건 |
|---|---|---|
| `00-existing-domain-enums.sql` | 현재 업무 엔티티의 PostgreSQL named enum 준비 | 타입이 없을 때 생성, 기존 타입 유지 |
| `../bootstrap/01-create-business-tables.sql` | 업무 테이블·관계 생성 | 업무 테이블이 없는 빈 DB |
| `01-create-rag-tables.sql` | 최종 RAG 테이블·FK·UNIQUE·페이지 제약 생성 | 업무 테이블은 있고 RAG 테이블은 없는 DB |
| `02-verify-rag.sql` | 확장·벡터 타입·FK·UNIQUE 확인 | RAG 테이블이 있는 DB |
| `legacy/` | 구버전 DB에 필요한 개별 변경 | 신규 설치에서는 실행하지 않음 |

테이블 생성 SQL은 각각 트랜잭션으로 실행하고 이미 테이블이 있으면 실패한다. 일괄 재실행해 기존 데이터를 덮어쓰는 방식은 사용하지 않는다. 업무 테이블이 이미 있으면 업무 생성 SQL을 건너뛰고, RAG 테이블까지 있으면 검증 SQL부터 실행한다. 일부만 준비된 DB에는 실제 스키마를 확인하고 필요한 파일만 적용한다.

## 컬럼 정책과 기존 DB

현재 RAG 생성 SQL과 엔티티에는 `document_version`, `effective_date`가 없다. 신규 DB는 이 두 컬럼을 만들거나 제거할 필요가 없다. 과거 두 컬럼이 남아 있는 DB만 [legacy 설명](legacy/README.md)에 따라 `legacy/03-remove-document-version-fields.sql` 적용 여부를 판단한다. 이 SQL은 모든 신규 설치 뒤에 실행하는 3단계가 아니다.

RAG `product_documents.created_at`, `updated_at`은 신규 SQL에서 NOT NULL 및 CURRENT_TIMESTAMP 기본값으로 생성한다. JPA auditing도 저장 시 값을 채운다. 예전에 Hibernate로 만든 DB의 nullable 타임스탬프를 이 작업에서 자동 변경하지 않는다. 실제 기존 DB의 제약을 변경하려면 별도로 검토한 변경 SQL이 필요하다.

일반 실행과 가이드에서 Hibernate update/create를 사용하지 않는다. 과거 초기 구축 내역은 [1단계 기록](../../docs/RAG_STAGE1_IMPLEMENTATION.md)이며 현재 설치 절차가 아니다.

## 모델·벡터 저장

Spring AI 모델 호출 모듈과 BOM을 사용한다. VectorStore starter와 별도 vector_store 테이블은 사용하지 않는다. 벡터 매핑은 hibernate-vector의 float[] / vector(1536), 검색은 기존 SQL이다. 상품 PK는 insurance_products.product_id bigint, 문서 유형은 TERMS/SUMMARY다.

pgvector 설치는 [공식 설치 문서](https://github.com/pgvector/pgvector#installation)를 따른다. 서버 설치와 대상 DB에서의 CREATE EXTENSION은 별도 단계다. 서버의 PostgreSQL 버전에 맞는 확장을 설치해야 한다.

## 실행과 검증

프로젝트 루트 `.env`에 DB 접속 정보와 API 키를 준비한다. 설정은 application.yaml 하나에 있으며 프로필 생략 시 local이 기본이다. 명시한 프로필이 있으면 해당 프로필을 사용한다. 원본 PDF 복사·메타데이터·적재 및 결과 확인은 [다른 PC 적재 가이드](../../docs/RAG_LOCAL_LOAD_GUIDE.md)를 참고한다.

```powershell
.\gradlew.bat bootRun
.\gradlew.bat ragIngest
```

`SchemaBootstrapPostgresTests`는 임시 스키마에서 enum·업무·RAG SQL을 실행한 뒤 전체 엔티티를 Hibernate validate로 확인한다. 구버전 컬럼 부재와 RAG 타임스탬프 정책도 검사하고 모든 DDL을 롤백한다. 테스트 실행에는 WONMATE_DB_URL 환경변수가 필요하다. 실제 기존 테이블을 재생성하지 않는다.
