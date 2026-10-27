package wonit.hackathon.a1.domain.todo.entity;

import wonit.hackathon.a1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import wonit.hackathon.a1.global.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "todos")
public class Todo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "todo_id")
    private Long id;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fc_id", nullable = false)
    private User user;

    private Todo(
            String content,
            LocalDate dueDate,
            User user
    ) {
        this.content = Objects.requireNonNull(content, "content");
        this.dueDate = dueDate;
        this.isCompleted = false;
        this.user = Objects.requireNonNull(user, "user");
    }

    public static Todo create(
            String content,
            LocalDate dueDate,
            User user
    ) {
        return new Todo(
                content,
                dueDate,
                user
        );
    }

    public void complete() { this.isCompleted = true; }

    public void reopen() { this.isCompleted = false; }

    public void update(String content, LocalDate dueDate) {
        this.content = Objects.requireNonNull(content, "content");
        this.dueDate = dueDate;
    }
}
