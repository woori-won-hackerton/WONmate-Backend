# 통합 보험 상담 RAG API 사용 예시

현재 구현 기준 문서다. 모델 호출 구성은 [Spring AI 전환 문서](RAG_SPRING_AI.md)를 참고한다. 처리 흐름·검색 품질 점검·검증 결과와 한계는 [6단계 구현 문서](RAG_STAGE6_IMPLEMENTATION.md)를 참고한다.

## API와 프론트 입력 흐름

- URL: `POST /api/rag/ask`
- Content-Type: `application/json; charset=utf-8`
- 응답: 최상위 `answer`, `sources` 유지.
- 사용자는 질문 유형을 선택하고 자연어 질문을 입력한다. **상품 설명일 때만 상품 하나를 선택한다.**
- 지원 유형은 용어 설명, 보험 개념 설명, 단일 상품 설명이다.

| 필드 | 타입 | 조건 | 설명 |
| --- | --- | --- | --- |
| question | string | 필수, 공백 불가, 최대 2,000자 | 설명받을 질문을 자연어로 입력 |
| questionType | string enum | 필수 | 아래 3가지 유형 중 하나 |
| productId | integer | 상품 설명에만 필수, 양수 | 문서 ID가 아닌 보험상품 ID. 다른 유형에는 생략/null |
| topK | integer | 선택, 기본 5, 범위 1~20 | 검색 1회당 청크 수. 최종 출처 수가 아님 |

| 화면 표시 | questionType | 상품 선택 |
| --- | --- | --- |
| 용어 설명 | TERM_EXPLANATION | 없음 |
| 보험 개념 설명 | INSURANCE_CONCEPT | 없음 |
| 상품 설명 | PRODUCT_EXPLANATION | 단일 productId 필수 |

유형 변경 시 프론트는 이전 productId를 비운다. 과거의 productIds 배열과 질문 자동 분류 방식은 현재 요청 계약에 해당하지 않는다.

## 유형별 요청 예시

모든 ID는 예시다. 실제 사용 전 현재 환경의 상품 ID와 문서 적재 상태를 확인한다.

### 1. 보험 용어 설명

```json
{
  "questionType": "TERM_EXPLANATION",
  "question": "면책기간과 감액기간이 무슨 뜻이고 어떻게 달라?",
  "topK": 5
}
```

검색된 문서의 용어와 사용 맥락을 설명한다. 특정 약관의 조건을 모든 상품의 공통 조건으로 단정하지 않는다. 문서에 관련 근거가 없으면 확인 불가로 안내할 수 있다.

### 2. 보험 개념 설명

```json
{
  "questionType": "INSURANCE_CONCEPT",
  "question": "갱신형 보험과 비갱신형 보험의 차이를 설명해줘"
}
```

productId 없이 요청한다. 일반 개념 질문도 현재는 RAG 문서 근거를 사용하며, 문서가 없는 개념을 일반 지식만으로 생성하는 별도 기능은 없다.

### 3. 선택 상품의 보장 설명

```json
{
  "questionType": "PRODUCT_EXPLANATION",
  "productId": 12,
  "question": "이 상품의 암 진단비 지급 조건과 면책·감액기간을 요약해줘",
  "topK": 5
}
```

선택 상품에서만 SUMMARY, 지급 조건 TERMS, 제한 조건 TERMS를 독립 검색한다. 상품명은 정식 명칭으로 답하고 지급 예시는 기준 가입금액과 적용 조건을 함께 설명하도록 지시한다.

입원 관련 질문도 동일한 유형으로 요청할 수 있다.

```json
{
  "questionType": "PRODUCT_EXPLANATION",
  "productId": 12,
  "question": "입원 관련 보장과 지급 횟수 제한을 찾아줘"
}
```

### 4. 상품 미지정 약관 질문

```json
{
  "questionType": "TERM_EXPLANATION",
  "question": "암보장개시일은 언제야?"
}
```

상품 미지정 상태에서는 문서별 조건 차이 가능성을 함께 안내한다. 특정 상품의 정확한 조건이 필요하면 PRODUCT_EXPLANATION과 해당 productId로 다시 요청한다.

## 호출 방법

PowerShell 예시다. 아래 주소는 개발 서버의 예시이며 실제 서버 주소·포트에 맞춰 변경한다.

```powershell
$ragBaseUrl = 'http://localhost:8081'
$ragBody = @{
    questionType = 'TERM_EXPLANATION'
    question = '면책기간과 감액기간의 차이를 설명해줘'
    topK = 5
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "$ragBaseUrl/api/rag/ask" `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($ragBody))
```

현재 구현은 한 요청의 질문만 처리한다. 후속 질문에 필요한 유형·상품을 다시 포함한다. “그 상품은?”처럼 앞 대화만 참조하는 질문을 해석하는 서버 대화 메모리는 없다.

## 응답과 출처 해석

다음은 **응답 필드 설명용 가상 예시**다. 실제 GPT 응답이나 현재 DB의 청크·페이지 조합을 재현한 결과가 아니다.

```json
{
  "answer": "암 진단비 지급 조건과 면책·감액 조건을 함께 확인해야 합니다. 지급 예시에는 기준 가입금액과 적용 조건이 있습니다.",
  "sources": [
    {
      "productId": 12,
      "documentId": 22,
      "documentName": "상품요약서_무배당우리WON하는실속하나로암보험_20260601.pdf",
      "documentType": "SUMMARY",
      "chunkId": 8027,
      "sectionTitle": null,
      "startPage": 4,
      "endPage": 5
    },
    {
      "productId": 12,
      "documentId": 22,
      "documentName": "상품요약서_무배당우리WON하는실속하나로암보험_20260601.pdf",
      "documentType": "SUMMARY",
      "chunkId": 8028,
      "sectionTitle": null,
      "startPage": 5,
      "endPage": 8
    }
  ]
}
```

| 필드 | 의미 |
| --- | --- |
| answer | 사용자용 자연어/Markdown 답변. 정식 상품명을 사용하며 내부 ID·PDF 파일명·경로·참고 문서 목록을 표시하지 않도록 지시 |
| sources | 답변 생성에 제공된 검색 근거 중 LLM이 답변 관련 항목으로 선택하고 서버가 검증한 결과. 실제 문장별 인용 관계는 보장하지 않음 |
| productId / documentId / chunkId | 서버 검색 결과의 식별자. LLM이 생성하는 값이 아님 |
| documentName | 원본 PDF 파일명 유지. 화면 표시용 정식 상품명이 아님 |
| documentType | TERMS(약관), SUMMARY(상품요약서) |
| sectionTitle | 저장된 청크 제목. null일 수 있으며 청크 전체 내용을 대표하지 못하는 기존 데이터도 있음 |
| startPage / endPage | 1부터 시작하는 물리적 PDF 페이지, 양끝 포함. PDF에 인쇄된 페이지 표기와 다를 수 있음 |

LLM은 DB ID 대신 요청 내 evidenceIndex만 받는다. 생성 결과의 evidenceIndexes를 백엔드가 범위 검증하고 실제 SearchResult에 연결한다. 같은 근거 순번은 중복 제거하지만 같은 문서의 다른 청크는 별도 sources로 유지한다. 이 내부 생성 형식은 공개 API에 추가되지 않는다.

예시처럼 동일 문서의 4~5쪽, 5~8쪽이 반환되면 프론트 표시 시 문서 하나와 참고 페이지 4~8로 묶는다. API 응답을 문서 단위로 합쳐 청크·페이지 정보를 버리지는 않는다. 현재 저장소에는 프론트 소스가 없어 그룹화 UI는 구현하지 않았다. 기존 UI에서 documentId 기준 그룹화·중복 페이지 제거·연속 구간 병합·읽기 쉬운 제목 표시가 별도로 필요하다.

근거가 없거나 선택된 근거가 없는 경우의 안내 예시:

```json
{
  "answer": "제공된 검색 근거만으로 질문의 내용을 확인할 수 없습니다. 해당 상품의 보장 및 면책·감액 조건을 원문에서 추가 확인해 주세요.",
  "sources": []
}
```

모델 거절·전송 실패·잘못된 응답 형식·범위를 벗어난 근거 순번은 정상 답변처럼 반환하지 않는다. 내부 식별자 표현 위반은 질문 유형과 위반 사실만 WARN에 기록하며 본문 자동 삭제나 복잡한 재시도는 하지 않는다.

## 잘못된 요청 예시

다음은 모두 HTTP 400 대상이다. 오류 응답 본문은 애플리케이션 예외 처리에 따르며 여기서는 고정 형식을 약속하지 않는다.

상품 설명인데 상품을 선택하지 않은 경우:

```json
{
  "questionType": "PRODUCT_EXPLANATION",
  "question": "보장을 요약해줘"
}
```

용어 설명인데 productId를 전송한 경우:

```json
{
  "questionType": "TERM_EXPLANATION",
  "productId": 12,
  "question": "면책기간의 뜻을 설명해줘"
}
```

그 외 questionType 누락/알 수 없는 값, 공백 질문, 0 이하 productId, 배열 형태 productId, 범위를 벗어난 topK도 검증 대상이다. 존재하지 않는 양수 상품 ID는 입력 형식 오류와 다르며 검색 근거가 없어 확인 불가 응답이 될 수 있다.

## 문서 적재 및 상품 ID 확인

PDF 임베딩·청크 적재는 프로젝트 루트에서 `gradlew.bat ragIngest`를 실행하는 CLI 방식만 지원한다. HTTP 문서 적재·업로드 API는 제공하지 않으며, 새 파일 추가나 서버 시작으로 자동 임베딩하지 않는다. 다른 컴퓨터의 초기 설정과 실행 명령은 [documents 적재 가이드](RAG_LOCAL_LOAD_GUIDE.md)를 참고한다.

`GET /api/documents`에서 문서의 productId, documentId, path, status, hash, chunkCount를 확인할 수 있다. 같은 상품의 약관·요약서에는 동일 productId와 서로 다른 documentId가 연결된다. 현재 이 목록 API는 local 프로필 전용이므로 운영 환경의 상품 선택 목록 API를 대신한다고 가정하지 않는다. 목록 응답에 정식 상품명 필드는 없다.

DB 연결과 모델 설정은 프로젝트의 환경 설정을 사용한다. 문서에는 실제 DB 주소·계정·비밀번호·API 키를 기재하지 않는다. 검색에는 임베딩 호출이, 답변 생성에는 모델 호출이 발생할 수 있으므로 예시를 실제 서버에 전송하면 비용이 발생할 수 있다. 이 문서 작업에서는 예시 호출을 실행하지 않았다.

기존 데이터의 청크 제목 개선에는 별도 재처리가 필요하다. 동일 파일의 완료 문서는 적재기가 건너뛰므로 단순 재실행만으로 별표 분리 변경이 적용되지는 않는다. 상세 원인과 검증 결과는 구현 문서를 참고한다.
