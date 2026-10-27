package wonit.hackathon.a1.domain.user.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import wonit.hackathon.a1.global.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "financial_consultants")
@Check(constraints = "(joined_year IS NULL OR joined_year BETWEEN 1900 AND 9999)")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fc_id")
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 100)
    private String loginId;


    /**
     * 암호화된 비밀번호를 저장하므로 최대 길이를 255로 지정
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "joined_year")
    private Short joinedYear;

    @Column(name = "branch_name", length = 100)
    private String branchName;

    private User(
            String loginId,
            String password,
            String email,
            String fullName,
            Short joinedYear,
            String branchName
    ) {
        this.loginId = Objects.requireNonNull(loginId, "loginId");
        this.password = Objects.requireNonNull(password, "password");
        this.email = Objects.requireNonNull(email, "email");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.joinedYear = joinedYear;
        this.branchName = branchName;
        if (joinedYear != null && (joinedYear < 1900 || joinedYear > 9999)) throw new IllegalArgumentException("입사년도를 확인하세요.");
    }

    public static User create(
            String loginId,
            String password,
            String email,
            String fullName,
            Short joinedYear,
            String branchName
    ) {
        return new User(
                loginId,
                password,
                email,
                fullName,
                joinedYear,
                branchName
        );
    }

    public void changePassword(String password) {
        this.password = Objects.requireNonNull(password, "password");
    }
}
