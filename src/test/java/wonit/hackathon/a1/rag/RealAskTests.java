package wonit.hackathon.a1.rag;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.service.rag.AskService;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: actual paid embeddings and GPT calls. No question/answer persistence. */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "RAG_ASK_LIVE", matches = "true")
class RealAskTests {
    @Autowired AskService service;
    @Autowired JdbcTemplate jdbc;
    @Test void answersConceptWithoutProduct() {
        var result = service.ask(new AskRequest("암 보장이란 무엇인가요?", QuestionType.INSURANCE_CONCEPT, null, 1));
        assertFalse(result.answer().isBlank());
        assertFalse(result.sources().isEmpty());
        assertTrue(result.sources().size() <= 15);
        System.out.println("LIVE UNFILTERED: " + result);
    }
    @Test void verifiesRealAnswersAndStoredSourceMetadata() {
        var questions = List.of("암 보장 내용을 설명해 주세요", "암 진단비 지급 조건은?", "암 진단비의 면책기간과 감액 조건은?",
                "약관에서 암의 정의는?", "이 상품은 화성 여행 중 외계인 납치 보상금을 얼마 지급하나요?");
        for (int i = 0; i < questions.size(); i++) {
            var result = service.ask(new AskRequest(questions.get(i), QuestionType.PRODUCT_EXPLANATION, 6L, 5));
            assertFalse(result.answer().isBlank());
            if (i == 4) assertTrue(result.answer().contains("확인") && result.sources().isEmpty());
            for (var source : result.sources()) {
                assertEquals(6L, source.productId());
                var row = jdbc.queryForMap("""
                        SELECT d.product_id, d.document_id, d.document_name, d.document_type,
                               c.section_title, c.start_page, c.end_page
                        FROM product_chunks c JOIN product_documents d USING(document_id) WHERE c.chunk_id=?
                        """, source.chunkId());
                assertEquals(source.documentId(), ((Number)row.get("document_id")).longValue());
                assertEquals(source.documentName(), row.get("document_name"));
                assertEquals(source.documentType().name(), row.get("document_type"));
                assertEquals(source.sectionTitle(), row.get("section_title"));
                assertEquals(source.startPage(), row.get("start_page"));
                assertEquals(source.endPage(), row.get("end_page"));
            }
            System.out.println("LIVE CASE " + (i + 1) + ": " + result.answer() + " SOURCES=" + result.sources());
        }
    }
}
