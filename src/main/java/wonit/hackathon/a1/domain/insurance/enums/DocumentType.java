package wonit.hackathon.a1.domain.insurance.enums;

/**
 * RAG 문서 종류 — documents/{상품 폴더}/metadata.json 의 documentType 값과 같게 유지합니다.
 * DB에는 varchar(TERMS · SUMMARY)로 저장합니다.
 */
public enum DocumentType {
    /** 약관 */
    TERMS,
    /** 상품요약서 */
    SUMMARY
}