package wonit.hackathon.a1.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/{loginId}")
    @Operation(summary = "사용자 정보 조회", description = "로그인 ID(사번)로 사용자 정보를 조회합니다.")
    public ApiResponse<UserResponse> getUser(
            @PathVariable("loginId") String loginId
    ) {
        UserResponse response = userService.getUser(loginId);

        return ApiResponse.onSuccess(
                "사용자 정보를 조회했습니다.",
                response
        );
    }
}
