package wonit.hackathon.a1.domain.consult.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;
import wonit.hackathon.a1.domain.consult.enums.ConsultType;
import wonit.hackathon.a1.domain.consult.enums.ConsultationStatus;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import wonit.hackathon.a1.global.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "consultations")
public class Consult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consultation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fc_customer_id", nullable = false)
    private FcCustomer fcCustomer;

    @Column(name = "scheduled_at", columnDefinition = "timestamptz")
    private OffsetDateTime scheduledAt;

    @Column(name = "purpose", columnDefinition = "text")
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "consult_type", nullable = false, length = 30)
    private ConsultType consultType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "consultation_status"
    )
    private ConsultationStatus status;

    @Column(name = "memo", columnDefinition = "text")
    private String memo;

    @Column(name = "memo_updated_at")
    private LocalDateTime memoUpdatedAt;

    /**
     * AI가 생성하고 상담사가 수정하는 상담 기록.
     * 생성 전에는 null입니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_record", columnDefinition = "jsonb")
    private JsonNode aiRecord;

    /** 상담 일정 확정 시각입니다. */
    @Column(name = "confirmed_at", columnDefinition = "timestamptz")
    private OffsetDateTime confirmedAt;

    /** 상담사가 AI 상담 기록을 검토하고 확정한 시각입니다. */
    @Column(name = "record_confirmed_at", columnDefinition = "timestamptz")
    private OffsetDateTime recordConfirmedAt;

    private Consult(
            FcCustomer fcCustomer,
            OffsetDateTime scheduledAt,
            ConsultType consultType,
            String purpose,
            String memo,
            JsonNode aiRecord,
            OffsetDateTime confirmedAt
    ) {
        this.fcCustomer = Objects.requireNonNull(fcCustomer, "fcCustomer");
        this.scheduledAt = scheduledAt;
        this.consultType = Objects.requireNonNull(consultType, "consultType");
        this.purpose = purpose;
        this.status = ConsultationStatus.draft;
        this.memo = memo;
        this.aiRecord = aiRecord == null ? null : aiRecord.deepCopy();
        this.confirmedAt = confirmedAt;
    }

    public static Consult create(
            FcCustomer fcCustomer,
            OffsetDateTime scheduledAt,
            ConsultType consultType,
            String purpose,
            String memo,
            JsonNode aiRecord,
            OffsetDateTime confirmedAt
    ) {
        return new Consult(
                fcCustomer,
                scheduledAt,
                consultType,
                purpose,
                memo,
                aiRecord,
                confirmedAt
        );
    }

    public void updateMemo(String memo) {
        this.memo = memo;
        this.memoUpdatedAt = LocalDateTime.now();
    }

    public void updatePurpose(String purpose) {
        this.purpose = purpose;
    }

    public void updateAiRecord(JsonNode aiRecord) {
        this.aiRecord = aiRecord == null ? null : aiRecord.deepCopy();
    }

    public JsonNode getAiRecord() {
        return aiRecord == null ? null : aiRecord.deepCopy();
    }

    public void changeStatus(ConsultationStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public void updateSchedule(
            OffsetDateTime scheduledAt,
            OffsetDateTime confirmedAt
    ) {
        this.scheduledAt = scheduledAt;
        this.confirmedAt = confirmedAt;
    }

    /** 최종 메모를 저장하고 상담을 완료합니다. */
    public void complete(String finalMemo) {
        updateMemo(Objects.requireNonNull(finalMemo, "finalMemo"));
        this.status = ConsultationStatus.completed;
    }

    /** 상담 기록을 확정합니다. 중복 호출 시 최초 확정 시각을 유지합니다. */
    public void confirmAiRecord() {
        if (this.recordConfirmedAt == null) {
            this.recordConfirmedAt = OffsetDateTime.now();
        }
    }
}