package wonit.hackathon.a1.dto.rag;

import jakarta.validation.constraints.*;
import java.util.List;

/** 질문 분류는 GPT가 해석한다. 상품 필터 생략/null/빈 배열은 전체 상품 검색이다. */
public record AskRequest(
        @NotBlank @Size(max = 2000) String question,
        // 특정 상품을 지정하는 경우에는 하나만 허용한다. 상품 비교 요청은 지원하지 않는다.
        @Size(max = 1) List<@NotNull @Positive Long> productIds,
        @Min(1) @Max(20) Integer topK) {
    public AskRequest { if (topK == null) topK = 5; }
}
