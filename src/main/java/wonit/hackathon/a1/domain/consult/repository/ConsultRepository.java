package wonit.hackathon.a1.domain.consult.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import wonit.hackathon.a1.domain.consult.entity.Consult;
import wonit.hackathon.a1.domain.consult.enums.ConsultationStatus;

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

    @Query("""
            select c
            from Consult c
            where c.fcCustomer.id = :fcCustomerId
              and c.id <> :currentConsultationId
              and c.status = :status
              and c.scheduledAt < :cutoff
            order by c.scheduledAt desc, c.id desc
            """)
    List<Consult> findBriefingHistories(
            @Param("fcCustomerId") Long fcCustomerId,
            @Param("currentConsultationId") Long currentConsultationId,
            @Param("status") ConsultationStatus status,
            @Param("cutoff") OffsetDateTime cutoff,
            Pageable pageable
    );

    // 준비 데이터가 아직 없을 때 동시 생성으로 중복 INSERT되는 것을 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consult c where c.id = :id")
    Optional<Consult> findByIdForBriefingUpdate(@Param("id") Long id);

}