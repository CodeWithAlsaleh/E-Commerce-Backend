package com.sivan.ecommerce.config;

import com.sivan.ecommerce.security.JwtAccessDeniedHandler;
import com.sivan.ecommerce.security.JwtAuthenticationEntryPoint;
import com.sivan.ecommerce.security.JwtAuthenticationFilter;
import com.sivan.ecommerce.security.JwtUtil;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * A dedicated test configuration that builds the Spring Security filter chain
 * for @WebMvcTest slices without requiring real environment variables.
 * <b></b>
 * By mocking JwtUtil, we prevent Spring Boot from attempting to inject the
 * ${ACCESS_TOKEN_EXPIRATION} properties, avoiding startup crashes while still
 * allowing the security filter chain to function correctly with @WithMockUser.
 */
@TestConfiguration
@Import({
        SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
public class TestSecurityConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(Mockito.mock(JwtUtil.class));
    }
}
