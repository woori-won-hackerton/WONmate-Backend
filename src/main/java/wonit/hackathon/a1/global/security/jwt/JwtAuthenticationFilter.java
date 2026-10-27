package wonit.hackathon.a1.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import wonit.hackathon.a1.global.security.CustomAuthenticationEntryPoint;
import wonit.hackathon.a1.global.security.CustomUserDetails;
import wonit.hackathon.a1.global.security.code.AuthErrorCode;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomAuthenticationEntryPoint entryPoint;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 기존 토큰이 만료되어도 다시 로그인할 수 있도록 제외
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/auth/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.substring(7).isBlank()) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(response, AuthErrorCode.TOKEN_INVALID);
            return;
        }

        try {
            String token = authorization.substring(7).trim();
            Claims claims = jwtTokenProvider.parseAndValidate(token);

            Long userId = Long.valueOf(claims.getSubject());
            CustomUserDetails principal = new CustomUserDetails(userId);

            var authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal,
                            null,
                            principal.getAuthorities()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(response, AuthErrorCode.TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(response, AuthErrorCode.TOKEN_INVALID);
            return;
        }

        // 뒤쪽 컨트롤러의 예외를 JWT 오류로 처리하지 않도록 try 밖에서 호출
        filterChain.doFilter(request, response);
    }
}