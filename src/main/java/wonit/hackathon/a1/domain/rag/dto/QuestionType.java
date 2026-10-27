package wonit.hackathon.a1.domain.rag.dto;

/** 챗봇 질문 유형 (프론트에서 선택) */
public enum QuestionType {
    /** 용어 설명 */
    TERM_EXPLANATION,
    /** 보험 개념 설명 */
    INSURANCE_CONCEPT,
    /** 상품 설명 — productId 필수 */
    PRODUCT_EXPLANATION
}