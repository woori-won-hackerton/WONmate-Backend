package wonit.hackathon.a1.domain.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "챗봇에서 고를 수 있는 상품 (적재 완료 문서가 있는 상품만)")
public record ProductResponse(

        @Schema(description = "상품 ID (챗봇 질문의 productId)", example = "1") Long productId,

        @Schema(description = "상품명", example = "무배당우리WON하는암보험") String productName,

        @Schema(description = "대분류 (종신보험 · 암보험 · 어린이보험 · 기타)", example = "암보험") String category,

        @Schema(description = "적재 완료된 문서 수", example = "2") long documentCount) {
}