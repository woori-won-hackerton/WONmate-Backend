package wonit.hackathon.a1.repository.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Long> {}
