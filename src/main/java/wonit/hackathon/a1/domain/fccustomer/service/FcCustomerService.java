package wonit.hackathon.a1.domain.fccustomer.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import wonit.hackathon.a1.domain.fccustomer.code.FcCustomerErrorCode;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;
import wonit.hackathon.a1.domain.fccustomer.repository.FcCustomerRepository;
import wonit.hackathon.a1.global.exception.GeneralException;


@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcCustomerService {

    private final FcCustomerRepository fcCustomerRepository;

    public void validateActiveAssignment(
            @NotNull @Positive Long fcId,
            @NotNull @Positive Long customerId
    ) {
        findActiveAssignment(fcId, customerId);
    }

    public Long getActiveAssignmentId(
            @NotNull @Positive Long fcId,
            @NotNull @Positive Long customerId
    ) {
        FcCustomer assignment =
                findActiveAssignment(fcId, customerId);

        return assignment.getId();
    }

    public void validateActiveAssignmentById(
            @NotNull @Positive Long fcId,
            @NotNull @Positive Long fcCustomerId
    ) {
        boolean exists =
                fcCustomerRepository
                        .existsByIdAndUser_IdAndAssignmentStatus(
                                fcCustomerId,
                                fcId,
                                AssignmentStatus.active
                        );

        if (!exists) {
            throw new GeneralException(
                    FcCustomerErrorCode.ASSIGNMENT_NOT_FOUND
            );
        }
    }

    private FcCustomer findActiveAssignment(
            Long fcId,
            Long customerId
    ) {
        return fcCustomerRepository
                .findByUser_IdAndCustomer_IdAndAssignmentStatus(
                        fcId,
                        customerId,
                        AssignmentStatus.active
                )
                .orElseThrow(() -> new GeneralException(
                        FcCustomerErrorCode.ASSIGNMENT_NOT_FOUND
                ));
    }
}