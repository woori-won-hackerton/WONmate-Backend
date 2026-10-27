package wonit.hackathon.a1.domain.rag.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;
import wonit.hackathon.a1.domain.insurance.entity.ProductChunk;
import wonit.hackathon.a1.domain.insurance.entity.ProductDocument;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.rag.ingest.ArticleChunker;
import wonit.hackathon.a1.domain.rag.ingest.ProductCategory;

/**
 * 문서 적재용 DB 작업 — 짧은 트랜잭션만 담당
 * (PDF 읽기 · 임베딩 같은 오래 걸리는 작업은 서비스에서 트랜잭션 밖에서 수행)
 */
@Repository
@Transactional(readOnly = true)
public class DocumentIngestionRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * metadata.json 의 상품 → insurance_products 의 상품 ID
     * - productId가 있으면 그 ID (상품명이 다르면 실패)
     * - 없으면 상품명이 정확히 같은 상품, 그것도 없으면 새로 만듦 (대분류는 상품명으로 추정)
     */
    @Transactional
    public Long resolveOrCreateProduct(Long declaredId, String productName) {
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Missing productName");
        }
        lock("rag-product:" + productName);

        if (declaredId != null) {
            InsuranceProduct product = em.find(InsuranceProduct.class, declaredId);
            if (product == null || !productName.equals(product.getProductName())) {
                throw new IllegalStateException("Declared productId does not match productName");
            }
            return declaredId;
        }

        List<Long> ids = em.createQuery(
                "select p.id from InsuranceProduct p where p.productName = :name", Long.class)
                .setParameter("name", productName)
                .getResultList();
        if (ids.size() > 1) {
            throw new IllegalStateException("Ambiguous productName");
        }
        if (ids.size() == 1) {
            return ids.get(0);
        }

        // 보험료 · 가입 연령 등은 메타데이터로 알 수 없어서 비워 둠
        InsuranceProduct created = InsuranceProduct.create(
                productName, null, null, ProductCategory.infer(productName), null, null);
        em.persist(created);
        em.flush();
        return created.getId();
    }

    /** 같은 파일(해시)로 이미 적재를 마쳤는지 */
    public boolean isCompleted(Long productId, DocumentType type, String storageUrl, String hash) {
        List<ProductDocument> docs = find(productId, type, storageUrl);
        if (docs.size() > 1) {
            throw new IllegalStateException("Duplicate document identity");
        }
        return !docs.isEmpty() && docs.get(0).isCompletedWith(hash);
    }

    public long countChunks() {
        return em.createQuery("select count(c) from ProductChunk c", Long.class).getSingleResult();
    }

    /**
     * 문서 한 건의 청크를 통째로 교체 (삭제 · 저장 · 완료 표시를 한 트랜잭션으로)
     * 
     * @return false = 그사이 다른 작업이 같은 파일로 이미 완료함
     */
    @Transactional
    public boolean replace(
            Long productId,
            DocumentType type,
            String storageUrl,
            String fileName,
            String hash,
            List<ArticleChunker.Chunk> chunks,
            List<float[]> vectors) {
        if (chunks.isEmpty() || chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("Invalid replacement chunks");
        }
        lock(productId + ":" + type + ":" + storageUrl);

        List<ProductDocument> existing = find(productId, type, storageUrl);
        if (existing.size() > 1) {
            throw new IllegalStateException("Duplicate document identity");
        }
        if (!existing.isEmpty() && existing.get(0).isCompletedWith(hash)) {
            return false;
        }

        ProductDocument document;
        if (existing.isEmpty()) {
            document = ProductDocument.create(
                    em.getReference(InsuranceProduct.class, productId), fileName, type, storageUrl, hash);
            em.persist(document);
        } else {
            document = existing.get(0);
            em.createQuery("delete from ProductChunk c where c.productDocument.id = :id")
                    .setParameter("id", document.getId())
                    .executeUpdate();
        }
        document.completeReplacement(fileName, hash);

        for (int i = 0; i < chunks.size(); i++) {
            ArticleChunker.Chunk c = chunks.get(i);
            em.persist(ProductChunk.create(
                    document, c.content(), vectors.get(i), c.startPage(), c.endPage(),
                    c.chunkIndex(), c.sectionTitle(), c.sectionPath()));
            // 청크가 수백 개라 주기적으로 비워 메모리 사용을 줄임
            if (i > 0 && i % 200 == 0) {
                em.flush();
                em.clear();
                document = em.find(ProductDocument.class, document.getId());
            }
        }
        em.flush();
        return true;
    }

    private List<ProductDocument> find(Long productId, DocumentType type, String storageUrl) {
        return em.createQuery("""
                select d from ProductDocument d
                where d.insuranceProduct.id = :id
                  and d.documentType = :type
                  and d.storageUrl = :path
                """, ProductDocument.class)
                .setParameter("id", productId)
                .setParameter("type", type)
                .setParameter("path", storageUrl)
                .getResultList();
    }

    /** 트랜잭션 동안 같은 키로 동시에 처리하지 않도록 PostgreSQL advisory lock */
    private void lock(String key) {
        em.createNativeQuery("select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)")
                .setParameter("key", key)
                .getSingleResult();
    }
}