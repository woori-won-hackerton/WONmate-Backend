package wonit.hackathon.a1.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import io.jsonwebtoken.Claims;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import wonit.hackathon.a1.global.security.jwt.JwtTokenProvider;
@Configuration
public class SecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder(JwtTokenProvider jwtTokenProvider) {
        return token -> {
            try {
                // 기존 코드로 서명, HS256, 발급자, 만료, 회원 ID 검증
                Claims claims = jwtTokenProvider.parseAndValidate(token);

                // 검증된 정보를 Spring Security의 Jwt 객체로 변환
                Jwt.Builder builder = Jwt.withTokenValue(token)
                        .header("alg", "HS256")
                        .claims(values -> values.putAll(claims))
                        .expiresAt(claims.getExpiration().toInstant());

                if (claims.getIssuedAt() != null) {
                    builder.issuedAt(claims.getIssuedAt().toInstant());
                }

                if (claims.getNotBefore() != null) {
                    builder.notBefore(claims.getNotBefore().toInstant());
                }

                return builder.build();

            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
                // Spring Security에서 인증 실패(401)로 처리
                throw new BadJwtException("유효하지 않은 JWT입니다.", e);
            }
        };
    }
    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }
    @Bean
    @ConditionalOnWebApplication(
            type = ConditionalOnWebApplication.Type.SERVLET
    )
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                // Authorization 헤더로만 인증하는 API 기준
                // 추후 Refresh Token 쿠키를 사용하면 CSRF 설정 재검토
                .csrf(AbstractHttpConfigurer::disable)

                // 별도의 CorsConfigurationSource Bean으로 허용 주소 설정
                .cors(Customizer.withDefaults())

                // 서버 세션으로 로그인 상태를 유지하지 않음
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 기본 로그인 화면 및 HTTP Basic 인증 비활성화
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 기본 세션 로그아웃 기능 비활성화
                .logout(AbstractHttpConfigurer::disable)

                // 인증 실패 요청을 세션에 저장하지 않음
                .requestCache(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // 회원가입과 로그인은 인증 없이 접근
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/signup",
                                "/api/auth/login"
                        ).permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // 오류 응답 처리 허용
                        .requestMatchers("/error").permitAll()

                        // 나머지 API는 인증 필요
                        .anyRequest().authenticated()
                )

                // 기본 Bearer Token 필터 + JwtDecoder로 JWT 검증
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}