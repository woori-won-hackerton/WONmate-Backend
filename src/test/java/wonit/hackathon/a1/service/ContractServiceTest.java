package wonit.hackathon.a1.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import wonit.hackathon.a1.domain.contract.entity.Contract;
import wonit.hackathon.a1.domain.contract.enums.ContractStatus;
import wonit.hackathon.a1.domain.contract.service.ContractService;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;
import wonit.hackathon.a1.domain.fccustomer.repository.FcCustomerRepository;
import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;
import wonit.hackathon.a1.domain.insurance.enums.PaymentFrequency;
import wonit.hackathon.a1.global.exception.GeneralException;
import wonit.hackathon.a1.global.security.code.AuthErrorCode;
import wonit.hackathon.a1.domain.contract.repository.ContractRepository;

class ContractServiceTest {
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final FcCustomerRepository assignments = mock(FcCustomerRepository.class);
    private final ContractService service = new ContractService(contracts, assignments);

    @BeforeEach
    void resetMocks() {
        reset(contracts, assignments);
    }

    @Test
    void rejectsCustomerWithoutAssignmentBeforeQueryingContracts() {
        when(assignments.findByUserIdAndCustomerId(1L, 2L)).thenReturn(Optional.empty());
        var error = assertThrows(GeneralException.class,
                () -> service.getContractByCustomerId(1L, 2L));
        assertEquals(AuthErrorCode.ACCESS_DENIED, error.getErrorCode());
        verifyNoInteractions(contracts);
    }

    @Test
    void rejectsInactiveAssignment() {
        var assignment = mock(FcCustomer.class);
        when(assignment.getAssignmentStatus()).thenReturn(AssignmentStatus.inactive);
        when(assignments.findByUserIdAndCustomerId(1L, 2L)).thenReturn(Optional.of(assignment));
        assertThrows(GeneralException.class, () -> service.getContractByCustomerId(1L, 2L));
        verifyNoInteractions(contracts);
    }

    @Test
    void returnsEmptyListForAssignedCustomerWithoutContracts() {
        allowAssignment();
        when(contracts.findAllByFcCustomerId(3L)).thenReturn(List.of());
        assertTrue(service.getContractByCustomerId(1L, 2L).isEmpty());
    }

    @Test
    void returnsRequiredFieldsAndOnlyMonthlyPremium() {
        allowAssignment();
        var product = mock(InsuranceProduct.class);
        var contract = mock(Contract.class);
        when(contract.getInsuranceProduct()).thenReturn(product);
        when(product.getProductName()).thenReturn("건강보험");
        when(product.getInsuranceType()).thenReturn("건강");
        when(product.getPaymentFrequency()).thenReturn(PaymentFrequency.monthly);
        when(contract.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(contract.getStatus()).thenReturn(ContractStatus.active);
        when(contract.getActualPremium()).thenReturn(new BigDecimal("35000.00"));
        when(contracts.findAllByFcCustomerId(3L)).thenReturn(List.of(contract));

        var response = service.getContractByCustomerId(1L, 2L).getFirst();
        assertEquals("건강보험", response.productName());
        assertEquals("건강", response.insuranceType());
        assertEquals(LocalDate.of(2026, 1, 1), response.startDate());
        assertEquals(ContractStatus.active, response.status());
        assertEquals(new BigDecimal("35000.00"), response.monthlyPremium());

        when(product.getPaymentFrequency()).thenReturn(PaymentFrequency.annual);
        assertNull(service.getContractByCustomerId(1L, 2L).getFirst().monthlyPremium());
        when(product.getPaymentFrequency()).thenReturn(null);
        assertNull(service.getContractByCustomerId(1L, 2L).getFirst().monthlyPremium());
    }

    private void allowAssignment() {
        var assignment = mock(FcCustomer.class);
        when(assignment.getId()).thenReturn(3L);
        when(assignment.getAssignmentStatus()).thenReturn(AssignmentStatus.active);
        when(assignments.findByUserIdAndCustomerId(1L, 2L)).thenReturn(Optional.of(assignment));
    }
}
