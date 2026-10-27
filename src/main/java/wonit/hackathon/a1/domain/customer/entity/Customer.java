package wonit.hackathon.a1.domain.customer.entity;

import wonit.hackathon.a1.domain.customer.enums.Gender;
import jakarta.persistence.*;
import java.time.LocalDate;
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
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gender", columnDefinition = "gender_code")
    private Gender gender;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "is_married")
    private Boolean isMarried;

    @Column(name = "has_children")
    private Boolean hasChildren;

    @Column(name = "has_disability")
    private Boolean hasDisability;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "residential_area", length = 200)
    private String residentialArea;

    private Customer(
            String fullName,
            LocalDate birthDate,
            Gender gender,
            String occupation,
            Boolean isMarried,
            Boolean hasChildren,
            Boolean hasDisability,
            String phoneNumber,
            String residentialArea
    ) {
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.birthDate = birthDate;
        this.gender = gender;
        this.occupation = occupation;
        this.isMarried = isMarried;
        this.hasChildren = hasChildren;
        this.hasDisability = hasDisability;
        this.phoneNumber = phoneNumber;
        this.residentialArea = residentialArea;
    }

    public static Customer create(
            String fullName,
            LocalDate birthDate,
            Gender gender,
            String occupation,
            Boolean isMarried,
            Boolean hasChildren,
            Boolean hasDisability,
            String phoneNumber,
            String residentialArea
    ) {
        return new Customer(
                fullName,
                birthDate,
                gender,
                occupation,
                isMarried,
                hasChildren,
                hasDisability,
                phoneNumber,
                residentialArea
        );
    }

    public void updateContact(String phoneNumber, String residentialArea) {
        this.phoneNumber = phoneNumber;
        this.residentialArea = residentialArea;
    }
}
