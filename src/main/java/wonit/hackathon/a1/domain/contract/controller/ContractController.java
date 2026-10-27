package wonit.hackathon.a1.domain.contract.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wonit.hackathon.a1.domain.contract.service.ContractService;
import wonit.hackathon.a1.domain.contract.dto.response.ContractResponse;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts")
@Tag(name = "Contract", description = "담당 고객의 보험계약 조회 API")
public class ContractController {

    private final ContractService contractService;
    @GetMapping("/{customerId}")
    @Operation(
            summary = "담당 고객의 보험계약 목록 조회",
            description = """
                    JWT로 인증된 FC의 활성 담당 고객에 연결된 보험계약을 조회합니다.
                    고객 상세 화면과 상담 준비 화면에서 공통으로 사용합니다.
                    모든 계약 상태를 포함하며 가입일 최신순, 같은 가입일이면 계약 ID 내림차순으로 반환합니다.
                    계약이 없으면 빈 목록을 반환합니다.
                    가입일은 계약 시작일이며 월납입금은 월납 계약의 실제 보험료입니다.
                    월납이 아니거나 납입 주기가 없으면 월납입금은 null입니다.
                    Authorization 헤더에 Bearer {accessToken}을 전달해야 합니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "보험계약 목록 조회 성공",
            content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "계약 목록", value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON2000",
                              "message": "고객 보험계약 목록 조회에 성공했습니다.",
                              "result": [
                                {
                                  "productName": "건강보험",
                                  "startDate": "2026-01-01",
                                  "insuranceType": "건강",
                                  "status": "active",
                                  "monthlyPremium": 35000.00
                                }
                              ],
                              "error": null
                            }
                            """),
                    @ExampleObject(name = "계약 없음", value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON2000",
                              "message": "고객 보험계약 목록 조회에 성공했습니다.",
                              "result": [],
                              "error": null
                            }
                            """)
            })
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "고객 ID가 양수가 아닌 경우", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "JWT가 없거나 유효하지 않거나 만료된 경우", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "담당 관계가 없거나 비활성 상태인 경우", content = @Content)
    public ApiResponse<List<ContractResponse>> getContractByCustomerId(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "조회할 담당 고객 ID", example = "1", required = true)
            @PathVariable(name = "customerId") Long customerId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ApiResponse.onSuccess(
                "고객 보험계약 목록 조회에 성공했습니다.",
                contractService.getContractByCustomerId(userId, customerId)
        );
    }
}
