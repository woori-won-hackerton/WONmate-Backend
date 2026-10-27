package wonit.hackathon.a1.domain.consult.enums;

/** AI 상담 기록 진행 상태 (DB 컬럼이 아니라 aiRecord · recordConfirmedAt 값으로 계산) */
public enum AiRecordStatus {
    /** 아직 AI 기록 없음 */
    NONE,
    /** AI 초안 생성됨 (수정 가능) */
    DRAFT,
    /** FC가 확정함 (수정 불가) */
    CONFIRMED
}