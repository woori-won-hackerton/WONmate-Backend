package wonit.hackathon.a1.domain.insurance.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import wonit.hackathon.a1.domain.insurance.entity.ProductChunk;

public interface ProductChunkRepository extends JpaRepository<ProductChunk, Long> {
    /** 특정 문서의 청크를 원문 순서로 조회한다. 벡터 유사도 검색은 수행하지 않는다. */
    List<ProductChunk> findByProductDocument_IdOrderByChunkIndexAsc(Long documentId);
}
