package wonit.hackathon.a1.dto.rag;

import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

public record SearchResult(Long chunkId, Long documentId, Long productId,
        String documentName, DocumentType documentType, String sectionTitle,
        String sectionPath, Integer startPage, Integer endPage,
        String content, double similarityScore) {}
