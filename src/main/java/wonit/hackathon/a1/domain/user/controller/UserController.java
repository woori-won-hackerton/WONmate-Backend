package wonit.hackathon.a1.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wonit.hackathon.a1.domain.user.dto.response.UserResponse;
import wonit.hackathon.a1.domain.user.service.UserService;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Tag(name = "사용자 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "로그인한 FC 본인의 정보를 조회합니다.")
    public ApiResponse<UserResponse> getMe(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Long fcId = Long.valueOf(jwt.getSubject());

        return ApiResponse.onSuccess(
                "사용자 정보를 조회했습니다.",
                userService.getMe(fcId));
    }

    @GetMapping("/{loginId}")
    @Operation(summary = "사용자 정보 조회", description = "로그인 ID(사번)로 사용자 정보를 조회합니다. 본인 정보만 조회할 수 있습니다.")
    public ApiResponse<UserResponse> getUser(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable("loginId") String loginId) {
        Long fcId = Long.valueOf(jwt.getSubject());

        UserResponse response = userService.getUser(loginId, fcId);

        return ApiResponse.onSuccess(
                "사용자 정보를 조회했습니다.",
                response);
    }
}