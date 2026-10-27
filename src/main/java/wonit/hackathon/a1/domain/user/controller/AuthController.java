package wonit.hackathon.a1.domain.user.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wonit.hackathon.a1.domain.user.dto.request.LoginRequest;
import wonit.hackathon.a1.domain.user.dto.response.LoginResponse;
import wonit.hackathon.a1.domain.user.service.AuthService;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "인증 API")
public class AuthController {

    private final AuthService authService;
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @RequestBody @Valid LoginRequest request
    ) {
        LoginResponse response = authService.login(request);

        return ApiResponse.onSuccess(
                "로그인에 성공했습니다.",
                response
        );
    }
}
