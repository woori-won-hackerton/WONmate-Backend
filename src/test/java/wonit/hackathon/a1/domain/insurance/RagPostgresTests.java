package wonit.hackathon.a1.domain.insurance;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;
import wonit.hackathon.a1.domain.insurance.entity.ProductChunk;
import wonit.hackathon.a1.domain.insurance.entity.ProductDocument;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.insurance.enums.IngestionStatus;
import wonit.hackathon.a1.repository.rag.ProductChunkRepository;
import wonit.hackathon.a1.repository.rag.ProductDocumentRepository;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 준비된 PostgreSQL 스키마에서 실행하는 통합 테스트.
 * WONMATE_DB_URL 환경 변수가 있어야 실행되며, .env 파일만으로는 실행 조건이 충족되지 않는다.
 * 임시 레코드는 테스트 종료 후 롤백하지만 ID 시퀀스 값은 소비될 수 있다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "WONMATE_DB_URL", matches = ".+")
class RagPostgresTests {
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductDocumentRepository documents;
    @Autowired ProductChunkRepository chunks;

    // 파일을 읽지 않고 테스트용 경로와 해시를 저장한다. PDF 존재나 실제 해시 계산 검사는 아니다.
    private ProductDocument saveDocument() {
        var product = InsuranceProduct.create("RAG mapping test", null, null, "TEST", null, null);
        entityManager.persist(product);
        return documents.saveAndFlush(ProductDocument.create(product, "통합약관_테스트.pdf",
                DocumentType.TERMS, "documents/test/통합약관_테스트.pdf", "a".repeat(64)));
    }

    @Test
    void repositoriesRoundTripNullAnd1536DimensionalVectorWithRelationsAndAuditing() {
        var document = saveDocument();
        assertEquals(IngestionStatus.PENDING, document.getIngestionStatus());
        assertNotNull(document.getCreatedAt());
        assertNotNull(document.getUpdatedAt());
        var vector = new float[1536];
        vector[0] = 0.25f;
        chunks.saveAndFlush(ProductChunk.create(document, "원문", vector, 1, 2, 0, "제1조", "주계약"));
        chunks.saveAndFlush(ProductChunk.create(document, "원문2", null, 3, 3, 1, null, null));
        var productId = document.getInsuranceProduct().getId();
        // 1차 캐시를 비워 저장 직후 객체가 아닌 DB에서 다시 읽은 값을 검증한다.
        entityManager.clear();
        var stored = chunks.findByProductDocument_IdOrderByChunkIndexAsc(document.getId());
        assertEquals(2, stored.size());
        assertArrayEquals(vector, stored.getFirst().getEmbedding());
        assertNull(stored.get(1).getEmbedding());
        assertEquals(productId, stored.getFirst().getProductDocument().getInsuranceProduct().getId());
        assertEquals(document.getId(), documents.findByInsuranceProduct_IdOrderByIdAsc(productId).getFirst().getId());
    }

    @Test
    void databaseRejectsDuplicateChunkIndexWithinDocument() {
        var document = saveDocument();
        chunks.saveAndFlush(ProductChunk.create(document, "원문", null, 1, 1, 0, null, null));
        assertThrows(DataIntegrityViolationException.class, () -> chunks.saveAndFlush(
                ProductChunk.create(document, "중복", null, 2, 2, 0, null, null)));
    }

    @Test
    void databaseHasVectorExtensionAndExactNullableColumnType() {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class));
        assertEquals("vector(1536)", jdbc.queryForObject("""
                SELECT format_type(atttypid, atttypmod) FROM pg_attribute
                WHERE attrelid = 'public.product_chunks'::regclass AND attname = 'embedding'
                  AND NOT attnotnull AND NOT attisdropped
                """, String.class));
    }
}
