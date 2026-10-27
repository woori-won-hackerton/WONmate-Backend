package wonit.hackathon.a1.dto.rag;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/** DB에 남는 문서 상태와 별개인 이번 시도의 결과. 오류 원문 대신 사유 또는 예외 종류만 반환한다. */
public record IngestionResult(
        @Schema(description = "documents/ 기준 상대 경로", example = "product_01/상품요약서.pdf") String path,
        @Schema(description = "이번 시도의 처리 상태", allowableValues = {"COMPLETED", "SKIPPED", "FAILED", "MAPPING_REQUIRED"}) String status,
        @Schema(description = "이번에 저장한 해당 문서의 청크 수. 생략/실패는 0", example = "17") int chunks,
        @Schema(description = "생략/실패/매핑 필요 사유. 성공 시 null", nullable = true) String reason,
        @Schema(description = "이미지 전용으로 OCR이 필요한 실제 PDF 페이지 번호(1부터 시작). OCR 자체는 수행하지 않음") List<Integer> ocrPages) {}
