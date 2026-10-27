package wonit.hackathon.a1.domain.consult.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
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
                        @Param("endAt") OffsetDateTime endAt);

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
                        @Param("userId") Long userId);

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
                        Pageable pageable);

        // 준비 데이터가 아직 없을 때 동시 생성으로 중복 INSERT되는 것을 방지
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select c from Consult c where c.id = :id")
        Optional<Consult> findByIdForBriefingUpdate(@Param("id") Long id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        select c
                        from Consult c
                        where c.id = :consultationId
                          and c.fcCustomer.user.id = :fcId
                        """)
        Optional<Consult> findOwnedForCompletion(
                        @Param("consultationId") Long consultationId,
                        @Param("fcId") Long fcId);

        /** 특정 상태의 상담 전체 (최근 순) — AI 상담 요약 목록용 */
        @Query("""
                        SELECT c
                        FROM Consult c
                        JOIN FETCH c.fcCustomer fc
                        JOIN FETCH fc.customer customer
                        WHERE fc.user.id = :userId
                          AND c.status = :status
                        ORDER BY c.scheduledAt DESC, c.id DESC
                        """)
        List<Consult> findAllByUserIdAndStatus(
                        @Param("userId") Long userId,
                        @Param("status") ConsultationStatus status,
                        Pageable pageable);

        /** 특정 고객 · 특정 상태의 상담 전체 (최근 순) */
        @Query("""
                        SELECT c
                        FROM Consult c
                        JOIN FETCH c.fcCustomer fc
                        JOIN FETCH fc.customer customer
                        WHERE fc.user.id = :userId
                          AND customer.id = :customerId
                          AND c.status = :status
                        ORDER BY c.scheduledAt DESC, c.id DESC
                        """)
        List<Consult> findAllByUserIdAndCustomerIdAndStatus(
                        @Param("userId") Long userId,
                        @Param("customerId") Long customerId,
                        @Param("status") ConsultationStatus status,
                        Pageable pageable);

        /**
         * 고객별 상담 통계 — [고객 ID(Long), 상담 수(Long), 가장 최근 상담 시각(OffsetDateTime)]
         * 담당 고객 목록 · 상세의 '상담 기록 수' · '최근 상담'에 사용
         */
        @Query("""
                        SELECT customer.id, COUNT(c), MAX(c.scheduledAt)
                        FROM Consult c
                        JOIN c.fcCustomer fc
                        JOIN fc.customer customer
                        WHERE fc.user.id = :userId
                          AND customer.id IN :customerIds
                          AND c.status = :status
                        GROUP BY customer.id
                        """)
        List<Object[]> findConsultStatsByCustomer(
                        @Param("userId") Long userId,
                        @Param("customerIds") Collection<Long> customerIds,
                        @Param("status") ConsultationStatus status);
}