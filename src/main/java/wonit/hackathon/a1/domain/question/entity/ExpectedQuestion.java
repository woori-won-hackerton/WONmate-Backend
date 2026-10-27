package wonit.hackathon.a1.domain.question.entity;

import wonit.hackathon.a1.domain.consultprepare.entity.ConsultPrepare;
import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import wonit.hackathon.a1.global.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "expected_questions")
public class ExpectedQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preparation_id", nullable = false)
    private ConsultPrepare consultPrepare;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    private ExpectedQuestion(
            ConsultPrepare consultPrepare,
            String question,
            String answer
    ) {
        this.consultPrepare = Objects.requireNonNull(consultPrepare, "consultPrepare");
        this.question = Objects.requireNonNull(question, "question");
        this.answer = answer;
    }

    public static ExpectedQuestion create(
            ConsultPrepare consultPrepare,
            String question,
            String answer
    ) {
        return new ExpectedQuestion(
                consultPrepare,
                question,
                answer
        );
    }

    public void updateAnswer(String answer) { this.answer = answer; }

    public void updateQuestion(String question) {
        this.question = Objects.requireNonNull(question, "question");
    }
}
