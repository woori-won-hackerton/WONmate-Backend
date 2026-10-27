package wonit.hackathon.a1.domain.insurance.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import wonit.hackathon.a1.domain.insurance.entity.ProductDocument;

public interface ProductDocumentRepository extends JpaRepository<ProductDocument, Long> {
    /** 특정 상품에 연결된 문서를 ID 순서로 조회한다. */
    List<ProductDocument> findByInsuranceProduct_IdOrderByIdAsc(Long productId);
}
