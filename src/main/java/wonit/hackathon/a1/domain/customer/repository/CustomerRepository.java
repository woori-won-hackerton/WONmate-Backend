package wonit.hackathon.a1.domain.customer.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import wonit.hackathon.a1.domain.customer.entity.Customer;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query(
            value = """
                    select c
                    from Customer c
                    where exists (
                        select fc.id
                        from FcCustomer fc
                        where fc.customer = c
                          and fc.user.id = :fcId
                          and fc.assignmentStatus = :status
                    )
                    and lower(c.fullName) like lower(:namePattern) escape '!'
                    """,
            countQuery = """
                    select count(c)
                    from Customer c
                    where exists (
                        select fc.id
                        from FcCustomer fc
                        where fc.customer = c
                          and fc.user.id = :fcId
                          and fc.assignmentStatus = :status
                    )
                    and lower(c.fullName) like lower(:namePattern) escape '!'
                    """
    )
    Page<Customer> findAssignedCustomers(
            @Param("fcId") Long fcId,
            @Param("status") AssignmentStatus status,
            @Param("namePattern") String namePattern,
            Pageable pageable
    );

    @Query("""
            select c
            from Customer c
            where c.id = :customerId
              and exists (
                  select fc.id
                  from FcCustomer fc
                  where fc.customer = c
                    and fc.user.id = :fcId
                    and fc.assignmentStatus = :status
              )
            """)
    Optional<Customer> findAssignedCustomer(
            @Param("fcId") Long fcId,
            @Param("customerId") Long customerId,
            @Param("status") AssignmentStatus status
    );

}
