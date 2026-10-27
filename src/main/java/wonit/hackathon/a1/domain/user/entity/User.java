package wonit.hackathon.a1.domain.user.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "financial_consultants")
@Check(constraints = "(joined_year IS NULL OR joined_year BETWEEN 1900 AND 9999)")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fc_id")
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 100)
    private String loginId;

    /** 서비스에서 인코딩한 비밀번호 해시만 전달합니다. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

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
            String passwordHash,
            String email,
            String fullName,
            Short joinedYear,
            String branchName
    ) {
        this.loginId = Objects.requireNonNull(loginId, "loginId");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.email = Objects.requireNonNull(email, "email");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.joinedYear = joinedYear;
        this.branchName = branchName;
        if (joinedYear != null && (joinedYear < 1900 || joinedYear > 9999)) throw new IllegalArgumentException("입사년도를 확인하세요.");
    }

    public static User create(
            String loginId,
            String passwordHash,
            String email,
            String fullName,
            Short joinedYear,
            String branchName
    ) {
        return new User(
                loginId,
                passwordHash,
                email,
                fullName,
                joinedYear,
                branchName
        );
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    }
}
