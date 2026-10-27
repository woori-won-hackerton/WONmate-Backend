package wonit.hackathon.a1.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import wonit.hackathon.a1.global.security.code.AuthErrorCode;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter responseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        responseWriter.write(response, AuthErrorCode.TOKEN_NOT_EXIST);
    }

    public void commence(
            HttpServletResponse response,
            AuthErrorCode errorCode
    ) throws IOException {
        responseWriter.write(response, errorCode);
    }
}