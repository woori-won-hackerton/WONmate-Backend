package wonit.hackathon.a1.domain.customerwiki.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import wonit.hackathon.a1.domain.customer.entity.Customer;
import wonit.hackathon.a1.domain.customer.repository.CustomerRepository;
import wonit.hackathon.a1.domain.customerwiki.dto.request.CustomerWikiAppendRequest;
import wonit.hackathon.a1.domain.customerwiki.dto.response.CustomerWikiResponse;
import wonit.hackathon.a1.domain.customerwiki.entity.CustomerWiki;
import wonit.hackathon.a1.domain.customerwiki.repository.CustomerWikiRepository;
import wonit.hackathon.a1.domain.fccustomer.code.FcCustomerErrorCode;
import wonit.hackathon.a1.domain.fccustomer.service.FcCustomerService;
import wonit.hackathon.a1.global.exception.GeneralException;


@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerWikiService {

    private final CustomerWikiRepository customerWikiRepository;
    private final CustomerRepository customerRepository;
    private final FcCustomerService fcCustomerService;


    public CustomerWikiResponse getWiki(
            @NotNull @Positive Long fcId,
            @NotNull @Positive Long customerId
    ) {
        fcCustomerService.validateActiveAssignment(
                fcId,
                customerId
        );

        return customerWikiRepository.findByCustomer_Id(customerId)
                .map(CustomerWikiResponse::from)
                .orElseGet(() ->
                        CustomerWikiResponse.empty(customerId)
                );
    }


    @Transactional
    public CustomerWikiResponse appendWiki(
            @NotNull @Positive Long fcId,
            @NotNull @Positive Long customerId,
            @NotNull @Valid CustomerWikiAppendRequest request
    ) {
        fcCustomerService.validateActiveAssignment(
                fcId,
                customerId
        );

        Customer customer = customerRepository
                .findByIdForUpdate(customerId)
                .orElseThrow(() -> new GeneralException(
                        FcCustomerErrorCode.ASSIGNMENT_NOT_FOUND
                ));

        CustomerWiki wiki = customerWikiRepository
                .findByCustomer_Id(customerId)
                .orElse(null);

        if (wiki == null) {
            wiki = CustomerWiki.create(
                    customer,
                    request.content()
            );

            wiki = customerWikiRepository.save(wiki);
        } else {
            wiki.appendContent(request.content());
        }

        return CustomerWikiResponse.from(wiki);
    }


}