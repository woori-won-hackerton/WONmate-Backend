package wonit.hackathon.a1.domain.insurance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import wonit.hackathon.a1.domain.insurance.enums.InsuranceType;

public record InsuranceProductResponse(
        @Schema(description = "상품 ID", example = "14") Long productId,
        @Schema(description = "상품명") String productName,
        @Schema(description = "상품 대분류 코드", example = "WHOLE_LIFE") String insuranceType
) {
    @Schema(description = "상품 대분류명", example = "종신보험")
    public String getInsuranceTypeName() {
        for (InsuranceType type : InsuranceType.values()) {
            if (type.name().equals(insuranceType)) {
                return type.getLabel();
            }
        }
        return InsuranceType.UNKNOWN.getLabel();
    }
}
