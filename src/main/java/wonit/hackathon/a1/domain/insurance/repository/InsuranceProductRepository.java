package wonit.hackathon.a1.domain.insurance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import wonit.hackathon.a1.domain.insurance.dto.response.InsuranceProductResponse;
import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Long> {
    @Query("""
            select new wonit.hackathon.a1.domain.insurance.dto.response.InsuranceProductResponse(
                p.id, p.productName, p.insuranceType)
            from InsuranceProduct p
            order by p.productName asc, p.id asc
            """)
    List<InsuranceProductResponse> findProductOptions();
}
