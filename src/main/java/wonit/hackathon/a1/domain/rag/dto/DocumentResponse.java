package wonit.hackathon.a1.domain.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.insurance.enums.IngestionStatus;

@Schema(description = "지식 자료실 문서")
public record DocumentResponse(

        Long documentId,
        Long productId,

        @Schema(description = "상품명") String productName,

        @Schema(description = "대분류 (종신보험 · 암보험 · 어린이보험 · 기타)") String category,

        @Schema(description = "화면 표시용 문서 이름 (예: 무배당…암보험 약관)") String title,

        @Schema(description = "원본 PDF 파일명") String fileName,

        @Schema(description = "TERMS(약관) · SUMMARY(상품요약서)") DocumentType documentType,

        @Schema(description = "적재 상태 (COMPLETED면 챗봇 검색 대상)") IngestionStatus status,

        @Schema(description = "청크 수") long chunkCount,

        @Schema(description = "마지막 적재 시각") LocalDateTime updatedAt) {
}