package wonit.hackathon.a1.domain.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import wonit.hackathon.a1.domain.customer.dto.response.CustomerPageResponse;
import wonit.hackathon.a1.domain.customer.dto.response.CustomerResponse;
import wonit.hackathon.a1.domain.customer.service.CustomerService;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;
import wonit.hackathon.a1.global.exception.GeneralException;
import wonit.hackathon.a1.global.security.code.AuthErrorCode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
@Tag(name = "고객 API")
public class CustomerController {

    private final CustomerService customerService;


    @GetMapping
    @Operation(summary = "담당 고객 목록 조회 및 이름 검색")
    public ApiResponse<CustomerPageResponse> getCustomers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Long fcId = getAuthenticatedFcId(jwt);

        CustomerPageResponse response =
                customerService.getCustomers(fcId, name, page, size);

        return ApiResponse.onSuccess(
                "담당 고객 목록 조회에 성공했습니다.",
                response
        );
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "담당 고객 기본 상세 조회")
    public ApiResponse<CustomerResponse> getCustomer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("customerId") Long customerId
    ) {
        Long fcId = getAuthenticatedFcId(jwt);

        CustomerResponse response =
                customerService.getCustomer(fcId, customerId);

        return ApiResponse.onSuccess(
                "고객 정보 조회에 성공했습니다.",
                response
        );
    }

    private Long getAuthenticatedFcId(Jwt jwt) {
        if (jwt == null) {
            throw new GeneralException(
                    AuthErrorCode.TOKEN_NOT_EXIST
            );
        }

        try {
            long fcId = Long.parseLong(jwt.getSubject());
            if (fcId <= 0) {
                throw new NumberFormatException();
            }
            return fcId;
        } catch (NumberFormatException e) {
            throw new GeneralException(AuthErrorCode.TOKEN_INVALID);
        }
    }
}
