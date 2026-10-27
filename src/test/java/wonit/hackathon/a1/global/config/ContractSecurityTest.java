package wonit.hackathon.a1.global.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import wonit.hackathon.a1.global.security.CustomAuthenticationEntryPoint;
import wonit.hackathon.a1.global.security.SecurityErrorResponseWriter;
import wonit.hackathon.a1.global.security.jwt.JwtTokenProvider;

class ContractSecurityTest {
    @Test
    void missingTokenRejectsContractRequestBeforeController() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(SecurityConfig.class, TestBeans.class);
            context.refresh();

            var request = new MockHttpServletRequest("GET", "/api/contracts/1");
            request.setServletPath("/api/contracts/1");
            var response = new MockHttpServletResponse();
            var reachedController = new AtomicBoolean();
            context.getBean("springSecurityFilterChain", FilterChainProxy.class)
                    .doFilter(request, response, (req, res) -> reachedController.set(true));

            assertEquals(401, response.getStatus());
            assertTrue(response.getContentAsString().contains("TOKEN_NOT_EXIST"));
            assertFalse(reachedController.get());
            verifyNoInteractions(context.getBean(JwtTokenProvider.class));
        }
    }

    @Configuration
    @EnableWebSecurity
    static class TestBeans {
        @Bean
        org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
            return new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return mock(JwtTokenProvider.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }

        @Bean
        CustomAuthenticationEntryPoint authenticationEntryPoint() {
            return new CustomAuthenticationEntryPoint(new SecurityErrorResponseWriter(new ObjectMapper()));
        }
    }
}
