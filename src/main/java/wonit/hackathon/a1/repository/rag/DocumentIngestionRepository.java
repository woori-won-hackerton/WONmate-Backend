package wonit.hackathon.a1.repository.rag;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import wonit.hackathon.a1.client.rag.EmbeddingClient;
import wonit.hackathon.a1.dto.rag.DocumentSummary;
import wonit.hackathon.a1.domain.insurance.entity.*;
import wonit.hackathon.a1.domain.insurance.enums.*;
import wonit.hackathon.a1.rag.ArticleChunker;

/** DB 접근과 짧은 트랜잭션만 담당한다. 외부 API 호출이나 PDF 파싱을 이 계층에 추가하지 않는다. */
@Repository
@Transactional(readOnly = true)
public class DocumentIngestionRepository {
    @PersistenceContext private EntityManager em;

    /** 명시된 기존 ID 또는 정확히 하나 일치하는 상품명만 허용한다. 없거나 모호하면 null을 반환한다. */
    public Long resolveProduct(Long productId, String name) {
        if (productId != null) return em.find(InsuranceProduct.class, productId) == null ? null : productId;
        var ids = em.createQuery("select p.id from InsuranceProduct p where p.productName = :name", Long.class)
                .setParameter("name", name).getResultList();
        return ids.size() == 1 ? ids.getFirst() : null;
    }

    public boolean isCompleted(Long productId, DocumentType type, String path, String hash) {
        return completed(find(productId, type, path), hash);
    }

    public long countChunks() {
        return em.createQuery("select count(c) from ProductChunk c", Long.class).getSingleResult();
    }

    /** LEFT JOIN 집계로 청크가 없는 문서도 포함하고 문서마다 별도 count 쿼리를 실행하지 않는다. */
    public List<DocumentSummary> list() {
        return em.createQuery("""
                select new wonit.hackathon.a1.dto.rag.DocumentSummary(
                    d.id, d.insuranceProduct.id, d.storageUrl, d.ingestionStatus, d.fileHash, count(c))
                from ProductDocument d left join ProductChunk c on c.productDocument = d
                group by d.id, d.insuranceProduct.id, d.storageUrl, d.ingestionStatus, d.fileHash
                order by d.id
                """, DocumentSummary.class).getResultList();
    }

    /**
     * 삭제·삽입·완료 상태/해시 갱신을 함께 커밋한다. 검증/저장 예외가 나면 기존 데이터를 복구한다.
     * 서비스가 모든 청크와 벡터를 확보한 뒤 호출하며, 반환 false는 다른 요청이 먼저 완료한 경우다.
     */
    @Transactional
    public boolean replace(Long productId, DocumentType type, String storage, String name, String hash,
                           List<ArticleChunker.Chunk> chunks,
                           List<float[]> vectors) {
        if (chunks.isEmpty() || chunks.size() != vectors.size()) throw new IllegalArgumentException("Invalid replacement chunks");
        // 신규 문서는 잠글 행이 없으므로 상품 ID·문서 유형·경로 기반의 트랜잭션 잠금을 사용한다.
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:identity, 0))")
                .setParameter("identity", productId + ":" + type + ":" + storage).getSingleResult();
        var existing = find(productId, type, storage);
        // 잠금을 기다리는 동안 다른 프로세스가 완료했을 수 있으므로 다시 검사한다.
        if (completed(existing, hash)) return false;
        var entity = existing.isEmpty() ? ProductDocument.create(em.getReference(InsuranceProduct.class, productId),
                name, type, storage, hash) : existing.getFirst();
        if (existing.isEmpty()) em.persist(entity);
        else em.createQuery("delete from ProductChunk c where c.productDocument.id=:id").setParameter("id", entity.getId()).executeUpdate();
        entity.completeReplacement(hash);
        for (int i = 0; i < chunks.size(); i++) {
            var c = chunks.get(i);
            if (vectors.get(i) == null) throw new IllegalArgumentException("Missing embedding");
            em.persist(ProductChunk.create(entity, c.content(), vectors.get(i), c.startPage(), c.endPage(), c.chunkIndex(), c.sectionTitle(), c.sectionPath()));
        }
        // 제약 조건/벡터 저장 오류를 트랜잭션 안에서 발견해 문서 단위로 롤백한다.
        em.flush();
        return true;
    }

    private List<ProductDocument> find(Long id, DocumentType type, String path) {
        return em.createQuery("select d from ProductDocument d where d.insuranceProduct.id=:id and d.documentType=:type and d.storageUrl=:path", ProductDocument.class)
                .setParameter("id", id).setParameter("type", type).setParameter("path", path).getResultList();
    }

    private boolean completed(List<ProductDocument> docs, String hash) {
        // 이미 중복된 데이터가 있으면 임의의 한 행을 선택해 덮어쓰지 않는다.
        if (docs.size() > 1) throw new IllegalStateException("Duplicate document identity");
        return !docs.isEmpty() && docs.getFirst().getIngestionStatus() == IngestionStatus.COMPLETED
                && docs.getFirst().getFileHash().equals(hash) && EmbeddingClient.MODEL.equals(docs.getFirst().getEmbeddingModel());
    }
}
