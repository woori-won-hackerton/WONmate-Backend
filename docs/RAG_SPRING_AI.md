# Spring AI 모델 호출 전환

## 현재 구조

모델 호출은 Spring AI 2.0.1의 `OpenAiChatModel`, `OpenAiEmbeddingModel`을 사용한다. `AiModelConfiguration`에서 빈을 명시적으로 구성하고 애플리케이션의 `ChatClient`, `EmbeddingClient`는 각각 `ChatModel`, `EmbeddingModel`을 주입받는다. 직접 작성한 HTTP 요청·인증 헤더·전송 JSON/응답 봉투 파싱은 제거했다.

DB 적재와 검색은 기존 `DocumentIngestionRepository`, `ChunkSearchRepository`를 유지한다. `product_documents`, `product_chunks`, `vector(1536)` 및 상품·문서 유형·완료 상태·모델 필터를 변경하지 않았다. Spring AI VectorStore를 사용하지 않으므로 스키마 변경이나 기존 문서 재임베딩이 필요하지 않다.

적재는 `ragIngest` CLI가 `DocumentIngestionService.ingestAll()`을 호출할 때만 실행한다. HTTP 적재 API와 단일 문서/상품 필터는 제거했다. 다른 컴퓨터의 실행 절차는 [documents 적재 가이드](RAG_LOCAL_LOAD_GUIDE.md)를 참고한다.

## 설정과 유지한 계약

| 항목 | 설정 / 동작 |
|---|---|
| API 키 | `.env` 또는 환경변수 `OPENAI_API_KEY` → `spring.ai.openai.api-key` |
| 답변 모델 | `OPENAI_CHAT_MODEL` → `spring.ai.openai.chat.options.model` |
| 답변 타임아웃 | 120초, 자동 재시도 없음 |
| 출력 | strict JSON schema의 `answer`, `evidenceIndexes`, `store:false` |
| 임베딩 모델 | 기존 데이터와 맞추기 위해 `text-embedding-3-small`, 1536차원 고정 |
| 임베딩 타임아웃 | `rag.embedding.timeout-seconds`, 기본 60초 |
| 배치 크기 | `rag.embedding.batch-size`, 기본 16 |
| 임베딩 재시도 | `rag.embedding.retries`, 기본 3회 추가. 429/5xx만 대상 |

SDK 자체 재시도는 두 모델 모두 0으로 설정한다. 임베딩 어댑터가 제한 재시도를 담당하여 중첩 재시도를 방지한다. 인증 오류·타임아웃은 재시도하지 않는다. 전송 오류의 원문이나 cause가 글로벌 예외 로그로 전달되지 않도록 안전한 예외로 변환한다. Spring AI 모델의 진단 로그가 빈 choices 응답에서 전체 프롬프트를 기록할 수 있어 local 설정의 `org.springframework.ai.openai` 로그 수준은 ERROR로 제한한다.

임베딩 응답은 index로 입력 순서를 복원하고 개수·중복/범위·1536차원·유한값·비영 벡터를 검증한다. 한 배치라도 실패하면 문서 교체 전에 중단한다. 답변은 완료 상태·거절·본문 형식·근거 순번을 검증한다. LLM에는 원문·정식 상품명·요청 내 순번을 전달하며 DB ID·파일명·페이지는 보내지 않는다.

현재 질문 유형은 `TERM_EXPLANATION`, `INSURANCE_CONCEPT`, `PRODUCT_EXPLANATION` 세 가지다. 상품 설명에만 단일 `productId`가 필요하다. 비교·추천 유형은 제거되어 HTTP 400으로 거부한다.

## 정리한 코드

- `spring-ai-starter-model-openai`, `spring-ai-starter-vector-store-pgvector` 대신 필요한 `spring-ai-openai` 모듈만 사용한다. 모델 빈을 직접 구성하여 사용하지 않는 모델·벡터 저장소 자동 설정을 로딩하지 않는다. Spring AI BOM은 버전 정렬에 필요하므로 유지한다.
- 중복 PostgreSQL 드라이버 선언을 버전이 명시된 `runtimeOnly` 하나로 통합했다.
- 효과가 없던 `AI_EMBEDDING_MODEL` 설정을 제거했다. 임베딩 모델은 코드·저장 데이터 계약에 따라 고정한다.
- 버려지는 임시 Map 대신 `validateScope`로 상품 범위를 검증한다.
- 운영 서비스에서 사용하지 않던 `ProductDocumentRepository`, `ProductChunkRepository`를 제거하고 DB 테스트는 EntityManager로 저장·조회한다. DB 제약 조건 검증은 유지한다.
- 호출처가 없던 `ApiResponse.onSuccess` 두 메서드와 전용 `BaseSuccessCode`, `GeneralSuccessCode`를 제거했다. 실제 오류 응답 처리는 유지한다.

## 검증

로컬 HTTP 모의 서버로 실제 Spring AI → SDK → HTTP 경로를 검증한다. 설정한 모델명, strict schema, store:false, 근거 메타데이터 제한, 거절·불완전·잘못된 응답, 인증·서버 오류를 확인한다. 임베딩은 배치·순서 복원·429/500 재시도·401 즉시 실패·타임아웃·잘못된 벡터 거부를 확인한다. 모델 설정 테스트는 모델명·차원·타임아웃과 SDK 재시도 비활성화를 확인한다.

DB 회귀 테스트는 실제 로컬 PostgreSQL에서 벡터 저장·상품 범위 검색·문서 교체·실패 롤백을 검증한다. 실제 OpenAI 유료 호출, PDF 전체 재적재 및 전체 PDF 감사는 이번 전환 검증에서 실행하지 않는다. 이전 전체 데이터 감사는 [7단계 기록](RAG_STAGE7_VALIDATION.md)을 참고한다.

2026-10-27 전체 회귀 실행: DB 연결 환경변수를 설정하고 `gradlew.bat test --offline` 실행, **43개 중 36개 통과·7개 skip·실패 0개**. opt-in인 전체 PDF 감사·렌더링·수동 검토·실제 API 테스트는 비활성화했다. 생성 오류 처리 케이스를 추가한 `ChatClientTests`도 별도 실행에 성공했다(21초). 마지막 HTML/XML 보고서는 해당 단일 테스트의 결과이며 전체 실행 집계는 이 기록을 참고한다.

응답 스키마의 근거는 [OpenAI 공식 구조화 출력 문서](https://developers.openai.com/api/docs/guides/structured-outputs)다. 프레임워크 사용만으로 검색 정확도나 답변의 의미적 정확성이 보장되지는 않는다.
