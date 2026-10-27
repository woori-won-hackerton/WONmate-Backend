package wonit.hackathon.a1.domain.insurance.enums;

/** 문서 적재(PDF → 청크 → 임베딩) 상태 */
public enum IngestionStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}