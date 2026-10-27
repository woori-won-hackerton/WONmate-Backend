package wonit.hackathon.a1.domain.fccustomer.entity;

import wonit.hackathon.a1.domain.customer.entity.Customer;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;
import wonit.hackathon.a1.domain.user.entity.User;
import jakarta.persistence.*;
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
@Table(name = "fc_customers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fc_id", "customer_id"})
})
public class FcCustomer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fc_customer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fc_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "assignment_status", nullable = false, columnDefinition = "assignment_status")
    private AssignmentStatus assignmentStatus;

    private FcCustomer(
            Customer customer,
            User user
    ) {
        this.customer = Objects.requireNonNull(customer, "customer");
        this.user = Objects.requireNonNull(user, "user");
        this.assignmentStatus = AssignmentStatus.active;
    }

    public static FcCustomer create(
            Customer customer,
            User user
    ) {
        return new FcCustomer(
                customer,
                user
        );
    }

    public void activate() { this.assignmentStatus = AssignmentStatus.active; }

    public void deactivate() { this.assignmentStatus = AssignmentStatus.inactive; }
}
