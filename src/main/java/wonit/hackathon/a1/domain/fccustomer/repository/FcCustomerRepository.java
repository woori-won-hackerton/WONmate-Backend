package wonit.hackathon.a1.domain.fccustomer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;

import java.util.Optional;

public interface FcCustomerRepository
        extends JpaRepository<FcCustomer, Long> {


    Optional<FcCustomer>
    findByUser_IdAndCustomer_IdAndAssignmentStatus(
            Long userId,
            Long customerId,
            AssignmentStatus assignmentStatus
    );

    boolean existsByIdAndUser_IdAndAssignmentStatus(
            Long id,
            Long userId,
            AssignmentStatus assignmentStatus
    );
}