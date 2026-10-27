package wonit.hackathon.a1.domain.consult.entity;

import tools.jackson.databind.JsonNode;
import wonit.hackathon.a1.domain.consult.enums.ConsultationStatus;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "consultation_status")
    private ConsultationStatus status;

    @Column(name = "memo", columnDefinition = "text")
    private String memo;

    /** AI가 생성한 상담 기록. 생성 전에는 null입니다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_record", columnDefinition = "jsonb")
    private JsonNode aiRecord;

    /** 상담 일정 확정 시각입니다. 상담 완료 시각이 아닙니다. */
    @Column(name = "confirmed_at", columnDefinition = "timestamptz")
    private OffsetDateTime confirmedAt;

    private Consult(
            FcCustomer fcCustomer,
            OffsetDateTime scheduledAt,
            String purpose,
            String memo,
            JsonNode aiRecord,
            OffsetDateTime confirmedAt
    ) {
        this.fcCustomer = Objects.requireNonNull(fcCustomer, "fcCustomer");
        this.scheduledAt = scheduledAt;
        this.purpose = purpose;
        this.status = ConsultationStatus.draft;
        this.memo = memo;
        this.aiRecord = aiRecord == null ? null : aiRecord.deepCopy();
        this.confirmedAt = confirmedAt;
    }

    public static Consult create(
            FcCustomer fcCustomer,
            OffsetDateTime scheduledAt,
            String purpose,
            String memo,
            JsonNode aiRecord,
            OffsetDateTime confirmedAt
    ) {
        return new Consult(
                fcCustomer,
                scheduledAt,
                purpose,
                memo,
                aiRecord,
                confirmedAt
        );
    }

    public void updateMemo(String memo) { this.memo = memo; }

    public void updatePurpose(String purpose) { this.purpose = purpose; }

    public void updateAiRecord(JsonNode aiRecord) {
        this.aiRecord = aiRecord == null ? null : aiRecord.deepCopy();
    }

    public JsonNode getAiRecord() {
        return aiRecord == null ? null : aiRecord.deepCopy();
    }

    // 상태 전이 규칙은 서비스 요구사항 확정 후 검증을 추가하세요.
    public void changeStatus(ConsultationStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public void updateSchedule(OffsetDateTime scheduledAt, OffsetDateTime confirmedAt) {
        this.scheduledAt = scheduledAt;
        this.confirmedAt = confirmedAt;
    }
}
