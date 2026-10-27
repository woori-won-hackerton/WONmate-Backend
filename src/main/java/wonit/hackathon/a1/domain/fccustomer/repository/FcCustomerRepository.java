package wonit.hackathon.a1.domain.fccustomer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;

import java.util.Optional;

public interface FcCustomerRepository
        extends JpaRepository<FcCustomer, Long> {

    Optional<FcCustomer> findByUser_IdAndCustomer_IdAndAssignmentStatus(
            Long userId,
            Long customerId,
            AssignmentStatus assignmentStatus
    );

    boolean existsByIdAndUser_IdAndAssignmentStatus(
            Long id,
            Long userId,
            AssignmentStatus assignmentStatus
    );

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
