package com.sivan.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Default strength is 10; you can set a higher "strength" (e.g., 12) for better security
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {

        http.authorizeHttpRequests(config ->
                config
                        .requestMatchers(HttpMethod.POST, "/products").hasRole("ADMIN")
                        .anyRequest().authenticated());

        // We need to tell Spring Security that we are using basic authentication
        http.httpBasic(Customizer.withDefaults());

        // Disable CSRF
        http.csrf(csrf -> csrf.disable());

        return http.build();
    }
}
