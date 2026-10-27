package wonit.hackathon.a1.domain.consult.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import wonit.hackathon.a1.domain.consult.entity.Consult;

public interface ConsultRepository extends JpaRepository<Consult, Long> {

    @Query("""
            SELECT c
            FROM Consult c
            JOIN FETCH c.fcCustomer fc
            JOIN FETCH fc.customer customer
            WHERE fc.user.id = :userId
              AND c.scheduledAt >= :startAt
              AND c.scheduledAt < :endAt
            ORDER BY c.scheduledAt ASC, c.id ASC
            """)
    List<Consult> findAllByUserIdAndScheduledRange(
            @Param("userId") Long userId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt
    );

    @Query("""
            SELECT c
            FROM Consult c
            JOIN FETCH c.fcCustomer fc
            JOIN FETCH fc.customer customer
            WHERE c.id = :consultId
              AND fc.user.id = :userId
            """)
    Optional<Consult> findByIdAndUserId(
            @Param("consultId") Long consultId,
            @Param("userId") Long userId
    );
}