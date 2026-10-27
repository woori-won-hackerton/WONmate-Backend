package wonit.hackathon.a1.domain.insurance.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Long> {

    List<InsuranceProduct> findAllByProductName(String productName);
}