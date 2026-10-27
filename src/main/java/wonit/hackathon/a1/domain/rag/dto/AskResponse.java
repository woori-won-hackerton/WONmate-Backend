package wonit.hackathon.a1.domain.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

/** sources는 답변에 쓰인 검색 근거(AI가 고른 것). 문장별 인용 관계를 보장하지는 않음 */
@Schema(description = "AI 챗봇 답변")
public record AskResponse(

        @Schema(description = "답변") String answer,

        @Schema(description = "근거 문서 (근거가 부족하면 빈 배열)") List<Source> sources) {

    public record Source(
            Long productId,
            @Schema(description = "상품명") String productName,
            Long documentId,
            @Schema(description = "화면 표시용 문서 이름 (예: 무배당…암보험 약관)") String documentName,
            @Schema(description = "TERMS(약관) · SUMMARY(상품요약서)") DocumentType documentType,
            Long chunkId,
            @Schema(description = "조항 제목") String sectionTitle,
            @Schema(description = "시작 페이지 (PDF 실제 페이지)") Integer startPage,
            @Schema(description = "끝 페이지") Integer endPage) {
    }
}