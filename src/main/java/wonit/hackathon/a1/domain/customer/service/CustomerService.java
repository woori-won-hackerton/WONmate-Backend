package wonit.hackathon.a1.domain.customer.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import wonit.hackathon.a1.domain.customer.code.CustomerErrorCode;
import wonit.hackathon.a1.domain.customer.dto.response.CustomerPageResponse;
import wonit.hackathon.a1.domain.customer.dto.response.CustomerResponse;
import wonit.hackathon.a1.domain.customer.entity.Customer;
import wonit.hackathon.a1.domain.customer.repository.CustomerRepository;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;
import wonit.hackathon.a1.global.exception.GeneralException;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private static final ZoneId SERVICE_ZONE =
            ZoneId.of("Asia/Seoul");

    private final CustomerRepository customerRepository;

    public CustomerPageResponse getCustomers(
            @NotNull @Positive Long fcId,
            String name,
            int page,
            int size
    ) {
        validatePageRequest(page, size);

        String namePattern = createNamePattern(name);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.asc("fullName"),
                        Sort.Order.asc("id")
                )
        );

        LocalDate today = LocalDate.now(SERVICE_ZONE);

        Page<CustomerResponse> result =
                customerRepository.findAssignedCustomers(
                        fcId,
                        AssignmentStatus.active,
                        namePattern,
                        pageable
                ).map(customer -> CustomerResponse.from(customer, today));

        return CustomerPageResponse.from(result);
    }


    public CustomerResponse getCustomer(
            @NotNull @Positive Long fcId,
            @NotNull @Positive Long customerId
    ) {
        Customer customer =
                customerRepository.findAssignedCustomer(
                        fcId,
                        customerId,
                        AssignmentStatus.active
                ).orElseThrow(() -> new GeneralException(
                        CustomerErrorCode.CUSTOMER_NOT_FOUND
                ));

        return CustomerResponse.from(
                customer,
                LocalDate.now(SERVICE_ZONE)
        );
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new GeneralException(
                    CustomerErrorCode.INVALID_PAGE_REQUEST
            );
        }
    }


    private String createNamePattern(String name) {
        if (name == null || name.isBlank()) {
            return "%";
        }

        String keyword = name.strip();

        if (keyword.length() > 100) {
            throw new GeneralException(
                    CustomerErrorCode.INVALID_SEARCH_NAME
            );
        }

        String escapedKeyword = keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + escapedKeyword + "%";
    }
}