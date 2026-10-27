package wonit.hackathon.a1.domain.rag.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import wonit.hackathon.a1.domain.insurance.entity.ProductDocument;
import wonit.hackathon.a1.domain.insurance.enums.IngestionStatus;

/** 챗봇 상품 목록 · 자료실 문서 목록 조회 */
@Repository
@Transactional(readOnly = true)
public class RagCatalogRepository {

    @PersistenceContext
    private EntityManager em;

    /** 적재 완료 문서가 있는 상품 — [상품 ID, 상품명, insurance_type, 문서 수] */
    public List<Object[]> findProductsWithCompletedDocuments() {
        return em.createQuery("""
                select p.id, p.productName, p.insuranceType, count(d)
                from ProductDocument d
                join d.insuranceProduct p
                where d.ingestionStatus = :status
                  and d.embeddingModel = :model
                group by p.id, p.productName, p.insuranceType
                order by p.productName
                """, Object[].class)
                .setParameter("status", IngestionStatus.COMPLETED)
                .setParameter("model", ProductDocument.EMBEDDING_MODEL)
                .getResultList();
    }

    /** 전체 문서 (상품 함께 조회) */
    public List<ProductDocument> findAllDocuments() {
        return em.createQuery("""
                select d from ProductDocument d
                join fetch d.insuranceProduct p
                order by p.productName, d.documentType
                """, ProductDocument.class)
                .getResultList();
    }

    public Optional<ProductDocument> findDocument(Long documentId) {
        return em.createQuery("""
                select d from ProductDocument d
                join fetch d.insuranceProduct
                where d.id = :id
                """, ProductDocument.class)
                .setParameter("id", documentId)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** 문서 ID → 청크 수 */
    public Map<Long, Long> countChunksByDocument() {
        List<Object[]> rows = em.createQuery("""
                select c.productDocument.id, count(c)
                from ProductChunk c
                group by c.productDocument.id
                """, Object[].class)
                .getResultList();
        Map<Long, Long> counts = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }
}