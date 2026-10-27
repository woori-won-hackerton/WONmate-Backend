package wonit.hackathon.a1.dto.rag;

import java.util.List;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

public record AskResponse(String answer, List<Source> sources) {
    public record Source(Long productId, Long documentId, String documentName,
            DocumentType documentType, Long chunkId, String sectionTitle, Integer startPage, Integer endPage) {
        public static Source from(SearchResult c) {
            return new Source(c.productId(), c.documentId(), c.documentName(), c.documentType(),
                    c.chunkId(), c.sectionTitle(), c.startPage(), c.endPage());
        }
    }
}
