package wonit.hackathon.a1.domain.rag.dto;

import java.util.List;

/**
 * 문서 한 건의 적재 결과
 * 
 * @param status   COMPLETED · SKIPPED(같은 파일이라 건너뜀) · FAILED
 * @param ocrPages 글자 없이 이미지만 있는 페이지 (OCR 필요, 검색 안 됨)
 */
public record IngestionResult(
        String path,
        String status,
        int chunks,
        String reason,
        List<Integer> ocrPages) {
}