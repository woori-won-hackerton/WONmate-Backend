package wonit.hackathon.a1.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import wonit.hackathon.a1.domain.contract.entity.Contract;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    @Query("""
            SELECT c
            FROM Contract c
            JOIN FETCH c.insuranceProduct
            WHERE c.fcCustomer.id = :fcCustomerId
            ORDER BY c.startDate DESC, c.id DESC
            """)
    List<Contract> findAllByFcCustomerId(@Param("fcCustomerId") Long fcCustomerId);
}
