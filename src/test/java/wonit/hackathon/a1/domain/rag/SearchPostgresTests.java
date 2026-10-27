package wonit.hackathon.a1.domain.rag;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import wonit.hackathon.a1.domain.insurance.entity.*;
import wonit.hackathon.a1.domain.insurance.enums.*;
import wonit.hackathon.a1.domain.rag.dto.SearchRequest;
import wonit.hackathon.a1.domain.rag.repository.ChunkSearchRepository;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "WONMATE_DB_URL", matches = ".+")
class SearchPostgresTests {
    @Autowired EntityManager em;
    @Autowired ChunkSearchRepository repository;

    private float[] vector(float x, float y) {
        var result = new float[1536]; result[0] = x; result[1] = y; return result;
    }
    private ProductDocument document(InsuranceProduct p, DocumentType type) {
        var d = ProductDocument.create(p, type + ".pdf", type, "test.pdf", "a".repeat(64));
        d.completeReplacement("a".repeat(64)); em.persist(d); return d;
    }
    private ProductChunk chunk(ProductDocument d, int index, float[] vector) {
        var c = ProductChunk.create(d, "원문 " + index, vector, 12, 13, index, "암의 정의", "특약 > 제2조");
        em.persist(c); return c;
    }

    @Test void filtersBeforeLimitRanksCosineAndPreservesSource() {
        var p = InsuranceProduct.create("검색 테스트", null, null, "TEST", null, null);
        var other = InsuranceProduct.create("다른 상품", null, null, "TEST", null, null);
        em.persist(p); em.persist(other);
        var terms = document(p, DocumentType.TERMS);
        var expected = chunk(terms, 0, vector(1, 1));
        chunk(terms, 1, vector(0, 1));
        chunk(terms, 2, null);
        chunk(terms, 3, vector(0, 0));
        chunk(document(p, DocumentType.SUMMARY), 0, vector(1, 0));
        chunk(document(other, DocumentType.TERMS), 0, vector(1, 0));
        var pending = document(p, DocumentType.TERMS); pending.updateIngestionStatus(IngestionStatus.PENDING);
        chunk(pending, 0, vector(1, 0));
        var wrongModel = document(p, DocumentType.TERMS); wrongModel.updateEmbeddingModel("other");
        chunk(wrongModel, 0, vector(1, 0));
        em.flush();
        var request = new SearchRequest("암의 정의", List.of(p.getId()), List.of(DocumentType.TERMS), 1);
        var results = repository.search(vector(1, 0), request);
        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(expected.getId(), result.chunkId());
        assertEquals(terms.getId(), result.documentId());
        assertEquals(p.getId(), result.productId());
        assertEquals("TERMS.pdf", result.documentName());
        assertEquals(DocumentType.TERMS, result.documentType());
        assertEquals("암의 정의", result.sectionTitle());
        assertEquals("특약 > 제2조", result.sectionPath());
        assertEquals(12, result.startPage()); assertEquals(13, result.endPage());
        assertEquals("원문 0", result.content());
        assertEquals(1 / Math.sqrt(2), result.similarityScore(), 1e-6);
        assertEquals(2, repository.search(vector(1, 0), new SearchRequest("질문", List.of(p.getId()), List.of(DocumentType.TERMS), 50)).size());
        assertTrue(repository.search(vector(1, 0), new SearchRequest("질문", List.of(Long.MAX_VALUE), null, 5)).isEmpty());
    }
}
