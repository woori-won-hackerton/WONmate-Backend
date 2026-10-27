package wonit.hackathon.a1.domain.insurance.enums;

/** 문서 적재 상태. 상태 저장만 제공하며 처리 워커는 아직 구현하지 않는다. */
public enum IngestionStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}
