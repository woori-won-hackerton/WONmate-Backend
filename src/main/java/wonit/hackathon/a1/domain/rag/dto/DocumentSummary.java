package wonit.hackathon.a1.domain.rag.dto;

import wonit.hackathon.a1.domain.insurance.enums.IngestionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** 저장된 문서의 현재 상태. 벡터와 청크 본문은 목록 응답에 포함하지 않는다. */
public record DocumentSummary(
        @Schema(description = "DB 문서 PK", example = "101") Long documentId,
        @Schema(description = "기존 보험상품 FK", example = "10") Long productId,
        @Schema(description = "저장된 PDF 경로. 기본 설정은 documents/로 시작", example = "documents/product_01/상품요약서.pdf") String path,
        @Schema(description = "DB에 저장된 현재 문서 상태") IngestionStatus status,
        @Schema(description = "저장된 PDF 바이너리의 SHA-256(64자리 16진수)") String hash,
        @Schema(description = "현재 이 문서에 연결된 청크 수", example = "17") long chunkCount) {}
