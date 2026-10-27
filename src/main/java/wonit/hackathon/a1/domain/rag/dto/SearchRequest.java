package wonit.hackathon.a1.domain.rag.dto;

import java.util.List;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

/** 내부 벡터 검색 조건 (productIds · documentTypes가 비어 있으면 전체) */
public record SearchRequest(
        List<Long> productIds,
        List<DocumentType> documentTypes,
        int topK) {
}