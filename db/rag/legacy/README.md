# 기존 DB 전용 변경 SQL

이 폴더는 과거 스키마를 사용하는 DB에 필요한 변경만 수동 적용하는 곳이다. 신규 설치 순서에 포함하지 않으며 애플리케이션이 자동 실행하지 않는다.

`03-remove-document-version-fields.sql`은 과거 `product_documents.document_version`, `effective_date`가 남아 있을 때만 사용하는 SQL이다. 두 컬럼에 저장된 값은 제거되므로 필요한 경우 먼저 백업한다. 문서 행·청크·임베딩은 삭제하지 않는다. 현재 생성 SQL과 엔티티는 이 두 컬럼을 처음부터 포함하지 않는다.
