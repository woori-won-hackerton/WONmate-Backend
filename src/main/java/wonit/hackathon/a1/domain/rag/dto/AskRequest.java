package wonit.hackathon.a1.domain.rag.dto;

import jakarta.validation.constraints.*;

/** 프론트에서 선택한 유형과 상품 설명용 단일 상품을 받는다. */
public record AskRequest(
        @NotBlank @Size(max = 2000) String question,
        @NotNull QuestionType questionType,
        @Positive Long productId,
        @Min(1) @Max(20) Integer topK) {
    public AskRequest { if (topK == null) topK = 5; }

    @AssertTrue(message = "상품 설명은 productId가 필수이며 다른 질문 유형에는 productId를 지정할 수 없습니다.")
    public boolean isProductSelectionValid() {
        return questionType == null || (questionType == QuestionType.PRODUCT_EXPLANATION
                ? productId != null : productId == null);
    }
}
