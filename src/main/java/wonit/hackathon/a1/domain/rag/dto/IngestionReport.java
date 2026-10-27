package wonit.hackathon.a1.domain.rag.dto;

import java.util.List;

/** 적재 전체 결과 */
public record IngestionReport(
        long completed,
        long skipped,
        long failed,
        int processedChunks,
        long totalChunks,
        List<IngestionResult> documents) {
}