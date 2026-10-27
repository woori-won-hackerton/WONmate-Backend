package wonit.hackathon.a1.domain.contract.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import wonit.hackathon.a1.domain.contract.enums.ContractStatus;

@Schema(description = "고객 보험계약 목록 항목")
public record ContractResponse(
        @Schema(description = "보험상품 이름", example = "건강보험") String productName,
        @Schema(description = "가입일(계약 시작일)", example = "2026-01-01") LocalDate startDate,
        @Schema(description = "보험 종류", example = "건강") String insuranceType,
        @Schema(description = "계약 상태 코드: active(유지), lapsed(실효), terminated(해지), matured(만기)",
                example = "active") ContractStatus status,
        @Schema(description = "월납 계약의 실제 보험료. 월납이 아니거나 주기가 없으면 null",
                example = "35000.00", nullable = true)
        BigDecimal monthlyPremium
) {
}
