package wonit.hackathon.a1.dto.rag;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/** 이번 요청의 처리 수와 DB 전체 청크 수를 구분한다. 문서별 실패가 있어도 다른 성공 결과는 유지된다. */
@Schema(description = "동기 적재 요청의 집계 결과. 매핑 필요도 failed에 포함합니다.")
public record IngestionReport(
        @Schema(description = "이번에 저장 또는 교체한 문서 수", example = "1") long success,
        @Schema(description = "동일 해시로 생략한 문서 수", example = "0") long skipped,
        @Schema(description = "실패 및 매핑 필요 건수. 메타데이터 오류는 폴더 단위 1건으로 집계할 수 있습니다.", example = "0") long failed,
        @Schema(description = "이번 요청에서 저장한 청크 수", example = "17") int processedChunks,
        @Schema(description = "요청 종료 시 DB 전체 청크 수", example = "17") long totalChunks,
        @Schema(description = "선택된 문서별 처리 결과. 메타데이터 오류는 폴더 경로로 보고합니다.") List<IngestionResult> documents) {}
