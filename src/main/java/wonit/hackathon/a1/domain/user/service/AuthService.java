package wonit.hackathon.a1.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import wonit.hackathon.a1.domain.user.dto.request.LoginRequest;
import wonit.hackathon.a1.domain.user.dto.response.LoginResponse;
import wonit.hackathon.a1.global.security.CustomUserDetails;
import wonit.hackathon.a1.global.security.jwt.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.loginId(),
                        request.password()
                )
        );

        CustomUserDetails userDetails =
                (CustomUserDetails) authentication.getPrincipal();

        String accessToken = jwtTokenProvider.createAccessToken(
                userDetails.getUserId()
        );

        return new LoginResponse(accessToken);
    }
}