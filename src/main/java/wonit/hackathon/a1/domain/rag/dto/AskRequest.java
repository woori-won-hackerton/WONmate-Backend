package wonit.hackathon.a1.domain.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "AI 챗봇 질문")
public record AskRequest(

        @Schema(description = "질문", example = "암 진단비 지급 조건과 면책·감액 기간을 알려줘") @NotBlank(message = "질문을 입력해주세요.") @Size(max = 2000, message = "질문은 2000자 이내로 입력해주세요.") String question,

        @Schema(description = "질문 유형", example = "PRODUCT_EXPLANATION") @NotNull(message = "질문 유형을 선택해주세요.") QuestionType questionType,

        @Schema(description = "상품 ID — 상품 설명일 때만 필수, 다른 유형에서는 생략", example = "1") @Positive Long productId,

        @Schema(description = "검색 1회당 문단 수 (기본 5, 1~20)", example = "5") @Min(1) @Max(20) Integer topK) {

    public AskRequest {
        if (topK == null) {
            topK = 5;
        }
    }

    @Schema(hidden = true)
    @AssertTrue(message = "상품 설명은 상품을 선택해야 하고, 다른 질문 유형은 상품을 선택할 수 없습니다.")
    public boolean isProductSelectionValid() {
        if (questionType == null) {
            return true;
        }
        return questionType == QuestionType.PRODUCT_EXPLANATION ? productId != null : productId == null;
    }
}