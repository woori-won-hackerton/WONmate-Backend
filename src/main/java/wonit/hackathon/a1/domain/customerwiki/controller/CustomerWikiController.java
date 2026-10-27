package wonit.hackathon.a1.domain.customerwiki.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import wonit.hackathon.a1.domain.customerwiki.dto.request.CustomerWikiAppendRequest;
import wonit.hackathon.a1.domain.customerwiki.dto.response.CustomerWikiResponse;
import wonit.hackathon.a1.domain.customerwiki.service.CustomerWikiService;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;
import wonit.hackathon.a1.global.exception.GeneralException;
import wonit.hackathon.a1.global.security.code.AuthErrorCode;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers/{customerId}/wiki")
@Tag(name = "고객 위키 API")
public class CustomerWikiController {

    private final CustomerWikiService customerWikiService;

    @GetMapping
    @Operation(summary = "고객 위키 조회")
    public ApiResponse<CustomerWikiResponse> getWiki(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("customerId") Long customerId
    ) {
        Long fcId = getAuthenticatedFcId(jwt);

        return ApiResponse.onSuccess(
                "고객 위키 조회에 성공했습니다.",
                customerWikiService.getWiki(fcId, customerId)
        );
    }

    @PostMapping("/entries")
    @Operation(summary = "고객 위키 내용 추가")
    public ApiResponse<CustomerWikiResponse> appendWiki(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("customerId") Long customerId,
            @Valid @RequestBody CustomerWikiAppendRequest request
    ) {
        Long fcId = getAuthenticatedFcId(jwt);

        return ApiResponse.onSuccess(
                "고객 위키 내용이 추가되었습니다.",
                customerWikiService.appendWiki(
                        fcId,
                        customerId,
                        request
                )
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
