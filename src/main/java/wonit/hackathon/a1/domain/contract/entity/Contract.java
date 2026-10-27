package wonit.hackathon.a1.domain.contract.entity;

import wonit.hackathon.a1.domain.contract.enums.ContractStatus;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "contracts")
@Check(constraints = "(actual_premium >= 0) AND (maturity_date IS NULL OR maturity_date >= start_date)")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fc_customer_id", nullable = false)
    private FcCustomer fcCustomer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct insuranceProduct;

    @Column(name = "actual_premium", nullable = false, precision = 15, scale = 2)
    private BigDecimal actualPremium;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "contract_status")
    private ContractStatus status;

    private Contract(
            FcCustomer fcCustomer,
            InsuranceProduct insuranceProduct,
            BigDecimal actualPremium,
            LocalDate startDate,
            LocalDate maturityDate
    ) {
        this.fcCustomer = Objects.requireNonNull(fcCustomer, "fcCustomer");
        this.insuranceProduct = Objects.requireNonNull(insuranceProduct, "insuranceProduct");
        this.actualPremium = Objects.requireNonNull(actualPremium, "actualPremium");
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.maturityDate = maturityDate;
        this.status = ContractStatus.active;
        if (actualPremium.signum() < 0) throw new IllegalArgumentException("보험료는 0 이상이어야 합니다.");
        if (maturityDate != null && maturityDate.isBefore(startDate)) throw new IllegalArgumentException("만기일은 시작일 이후여야 합니다.");
    }

    public static Contract create(
            FcCustomer fcCustomer,
            InsuranceProduct insuranceProduct,
            BigDecimal actualPremium,
            LocalDate startDate,
            LocalDate maturityDate
    ) {
        return new Contract(
                fcCustomer,
                insuranceProduct,
                actualPremium,
                startDate,
                maturityDate
        );
    }

    // 허용 상태 전이 정책은 서비스에서 검증합니다.
    public void changeStatus(ContractStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }
}
