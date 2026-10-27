package wonit.hackathon.a1.rag;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.dto.rag.SearchRequest;
import wonit.hackathon.a1.service.rag.ChunkSearchService;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: five paid embeddings per product; full search results stay in ignored build output. */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "RAG_SEARCH_REVIEW", matches = "true")
class RealSearchReviewTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ChunkSearchService service;

    @Test void savesFiveQuestionsWithOriginalChunksForManualReview() throws Exception {
        var questions = List.of("암 진단비 지급 조건", "면책기간", "암의 정의", "입원 보장", "보험금 지급 제한");
        var products = jdbc.queryForList("""
                SELECT DISTINCT d.product_id FROM product_documents d JOIN product_chunks c USING(document_id)
                WHERE d.ingestion_status='COMPLETED' AND d.embedding_model='text-embedding-3-small'
                  AND d.document_type='TERMS' AND c.embedding IS NOT NULL AND vector_norm(c.embedding)>0
                ORDER BY d.product_id
                """, Long.class);
        var report = new LinkedHashMap<String, Object>();
        report.put("questions", questions);
        report.put("storedChunks", jdbc.queryForObject("SELECT count(*) FROM product_chunks", Long.class));
        report.put("searchableProducts", products);
        var reviews = new ArrayList<Object>();
        report.put("searches", reviews);
        report.put("status", products.isEmpty() ? "BLOCKED_NO_SEARCHABLE_TERMS" : "RESULTS_REQUIRE_MANUAL_REVIEW");
        Path output = Path.of("build/rag-search/review.json");
        Files.createDirectories(output.getParent());
        var mapper = JsonMapper.builder().build();
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        for (var product : products) {
            for (var question : questions) {
                var results = service.search(new SearchRequest(question, List.of(product), List.of(DocumentType.TERMS), 5));
                assertTrue(results.stream().allMatch(r -> r.productId().equals(product) && r.documentType() == DocumentType.TERMS));
                assertTrue(results.stream().allMatch(r -> Double.isFinite(r.similarityScore()) && r.startPage() >= 1 && r.endPage() >= r.startPage()));
                reviews.add(Map.of("productId", product, "question", question, "results", results));
                Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            }
        }
    }
}
