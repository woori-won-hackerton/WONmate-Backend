package wonit.hackathon.a1.domain.consultprepare.entity;

import wonit.hackathon.a1.domain.consult.entity.Consult;
import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import wonit.hackathon.a1.global.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "consultation_preparations")
public class ConsultPrepare extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preparation_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false, unique = true)
    private Consult consult;

    @Column(name = "summary_briefing", columnDefinition = "text")
    private String summaryBriefing;

    private ConsultPrepare(
            Consult consult,
            String summaryBriefing
    ) {
        this.consult = Objects.requireNonNull(consult, "consult");
        this.summaryBriefing = summaryBriefing;
    }

    public static ConsultPrepare create(
            Consult consult,
            String summaryBriefing
    ) {
        return new ConsultPrepare(
                consult,
                summaryBriefing
        );
    }

    public void updateSummaryBriefing(String summaryBriefing) {
        this.summaryBriefing = summaryBriefing;
    }
}
