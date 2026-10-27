package wonit.hackathon.a1.domain.insurance.entity;

import wonit.hackathon.a1.domain.insurance.enums.PaymentFrequency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "insurance_products")
@Check(constraints = "(base_premium IS NULL OR base_premium >= 0) AND (payment_term_months IS NULL OR payment_term_months > 0)")
public class InsuranceProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "payment_term_months")
    private Integer paymentTermMonths;

    @Column(name = "base_premium", precision = 15, scale = 2)
    private BigDecimal basePremium;

    @Column(name = "insurance_type", nullable = false, length = 50)
    private String insuranceType;

    @Column(name = "eligible_age_description", length = 200)
    private String eligibleAgeDescription;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "payment_frequency", columnDefinition = "payment_frequency")
    private PaymentFrequency paymentFrequency;

    private InsuranceProduct(
            String productName,
            Integer paymentTermMonths,
            BigDecimal basePremium,
            String insuranceType,
            String eligibleAgeDescription,
            PaymentFrequency paymentFrequency
    ) {
        this.productName = Objects.requireNonNull(productName, "productName");
        this.paymentTermMonths = paymentTermMonths;
        this.basePremium = basePremium;
        this.insuranceType = Objects.requireNonNull(insuranceType, "insuranceType");
        this.eligibleAgeDescription = eligibleAgeDescription;
        this.paymentFrequency = paymentFrequency;
        if (basePremium != null && basePremium.signum() < 0) throw new IllegalArgumentException("보험료는 0 이상이어야 합니다.");
        if (paymentTermMonths != null && paymentTermMonths <= 0) throw new IllegalArgumentException("납입기간은 양수여야 합니다.");
    }

    public static InsuranceProduct create(
            String productName,
            Integer paymentTermMonths,
            BigDecimal basePremium,
            String insuranceType,
            String eligibleAgeDescription,
            PaymentFrequency paymentFrequency
    ) {
        return new InsuranceProduct(
                productName,
                paymentTermMonths,
                basePremium,
                insuranceType,
                eligibleAgeDescription,
                paymentFrequency
        );
    }
}
