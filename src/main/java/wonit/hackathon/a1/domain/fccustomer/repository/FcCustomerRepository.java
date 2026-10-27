package wonit.hackathon.a1.domain.fccustomer.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;

public interface FcCustomerRepository
        extends JpaRepository<FcCustomer, Long> {

    @Query("""
            SELECT fc
            FROM FcCustomer fc
            JOIN FETCH fc.customer customer
            WHERE fc.user.id = :userId
              AND customer.id = :customerId
            """)
    Optional<FcCustomer> findByUserIdAndCustomerId(
            @Param("userId") Long userId,
            @Param("customerId") Long customerId
    );
}