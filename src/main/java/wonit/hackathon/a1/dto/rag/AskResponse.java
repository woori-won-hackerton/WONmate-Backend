package wonit.hackathon.a1.dto.rag;

import java.util.List;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

/** sources는 답변 생성에 제공된 검색 근거 중 선택된 항목이며 실제 인용 관계를 보장하지 않는다. */
public record AskResponse(String answer, List<Source> sources) {
    public record Source(Long productId, Long documentId, String documentName,
            DocumentType documentType, Long chunkId, String sectionTitle, Integer startPage, Integer endPage) {
        public static Source from(SearchResult c) {
            return new Source(c.productId(), c.documentId(), c.documentName(), c.documentType(),
                    c.chunkId(), c.sectionTitle(), c.startPage(), c.endPage());
        }
    }
}
