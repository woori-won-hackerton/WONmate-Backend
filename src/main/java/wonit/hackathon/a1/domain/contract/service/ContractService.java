package wonit.hackathon.a1.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import wonit.hackathon.a1.domain.contract.dto.response.ContractResponse;
import wonit.hackathon.a1.domain.contract.entity.Contract;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;
import wonit.hackathon.a1.domain.fccustomer.repository.FcCustomerRepository;
import wonit.hackathon.a1.domain.insurance.enums.PaymentFrequency;
import wonit.hackathon.a1.global.code.GeneralErrorCode;
import wonit.hackathon.a1.global.exception.GeneralException;
import wonit.hackathon.a1.global.security.code.AuthErrorCode;
import wonit.hackathon.a1.domain.contract.repository.ContractRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractService {

    private final ContractRepository contractRepository;
    private final FcCustomerRepository fcCustomerRepository;

    public List<ContractResponse> getContractByCustomerId(Long userId, Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }

        var fcCustomer = fcCustomerRepository.findByUserIdAndCustomerId(userId, customerId)
                .filter(assignment -> assignment.getAssignmentStatus() == AssignmentStatus.active)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.ACCESS_DENIED));

        return contractRepository.findAllByFcCustomerId(fcCustomer.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ContractResponse toResponse(Contract contract) {
        var product = contract.getInsuranceProduct();
        return new ContractResponse(
                product.getProductName(),
                contract.getStartDate(),
                product.getInsuranceType(),
                contract.getStatus(),
                product.getPaymentFrequency() == PaymentFrequency.monthly
                        ? contract.getActualPremium() : null
        );
    }
}
