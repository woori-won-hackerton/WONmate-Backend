package wonit.hackathon.a1.rag;

import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** 명시적 opt-in. DB 읽기만 수행하며 임베딩/LLM 호출과 재적재는 하지 않는다. */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("local")
@Transactional(readOnly = true)
@EnabledIfEnvironmentVariable(named = "RAG_QUALITY_REVIEW", matches = "true")
class ChunkQualityReviewTests {
    @Autowired JdbcTemplate jdbc;

    @Test void inspectReportedChunksAndTheirOriginalText() throws Exception {
        var chunks = jdbc.queryForList("""
                SELECT c.chunk_id, c.document_id, d.product_id, p.product_name, d.document_type,
                       c.section_title, c.section_path, c.start_page, c.end_page, c.content, d.storage_url
                FROM product_chunks c JOIN product_documents d USING(document_id)
                JOIN insurance_products p USING(product_id)
                WHERE c.chunk_id IN (7258, 6309) ORDER BY c.chunk_id
                """);
        Files.createDirectories(Path.of("build/rag-quality"));
        Files.writeString(Path.of("build/rag-quality/reported-chunks.json"),
                JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(chunks));
        assertEquals(2, chunks.size(), "Reported chunks absent from current database; do not infer their content");
        var comparisons = new ArrayList<Map<String, Object>>();
        for (var stored : chunks) {
            var pdf = Path.of(stored.get("storage_url").toString()).toRealPath();
            assertTrue(pdf.startsWith(Path.of("documents").toRealPath()));
            var pages = new ArrayList<PdfExtractor.Page>();
            try (var document = Loader.loadPDF(pdf.toFile())) {
                var stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setLineSeparator("\n");
                for (int page = ((Number) stored.get("start_page")).intValue();
                     page <= ((Number) stored.get("end_page")).intValue(); page++) {
                    stripper.setStartPage(page); stripper.setEndPage(page);
                    pages.add(new PdfExtractor.Page(page, stripper.getText(document).replace("\r\n", "\n")));
                }
            }
            var split = new ArticleChunker(new ArticleChunker.Settings(750, 1000, 80, 1.5)).chunk(pages);
            var payment = split.stream().filter(c -> c.content().contains("암진단비")
                    && c.sectionTitle() != null && c.sectionTitle().contains("지급기준표")).findFirst().orElseThrow();
            assertFalse(payment.sectionPath().contains("예금보험"));
            assertFalse(payment.sectionPath().contains("중입자"));
            assertEquals(52, payment.startPage());
            assertTrue(payment.content().contains("1,000"));
            assertTrue(split.stream().anyMatch(c -> c.content().contains("예금자보호법")));
            comparisons.add(Map.of("before", stored, "after", split));
        }
        Files.writeString(Path.of("build/rag-quality/chunk-boundary-comparison.json"),
                JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(comparisons));
    }
}
