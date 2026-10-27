package wonit.hackathon.a1.domain.consult.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConsultType {

    NEW("신규 상담"),
    CONTRACT_REVIEW("계약 점검"),
    RENEWAL("갱신 상담"),
    CLAIM_GUIDANCE("보험금 청구 안내");

    private final String description;
}