package wonit.hackathon.a1.domain.rag.dto;

import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

/** 벡터 검색 결과 한 건 (similarityScore = 1 - 코사인 거리) */
public record SearchResult(
        Long chunkId,
        Long documentId,
        Long productId,
        String productName,
        String documentName,
        DocumentType documentType,
        String sectionTitle,
        String sectionPath,
        Integer startPage,
        Integer endPage,
        String content,
        double similarityScore) {
}