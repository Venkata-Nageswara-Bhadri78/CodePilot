package com.developer.copilot.auth.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.developer.copilot.auth.jwt.JwtAuthenticationFilter;
import com.developer.copilot.auth.ratelimit.config.AuthRateLimitConfig;
import com.developer.copilot.auth.ratelimit.filter.AuthRateLimitFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@Import({JsonAuthenticationEntryPoint.class, AuthRateLimitConfig.class})
@EnableConfigurationProperties({CorsProperties.class, AuthProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jsonAuthenticationEntryPoint))
        .authorizeHttpRequests(auth -> {
            if (!isProductionProfile()) {
                auth.requestMatchers(SWAGGER_PATHS).permitAll();
            }
            auth.requestMatchers(
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/verify-email",
                "/api/v1/auth/resend-otp",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password",
                "/api/v1/auth/refresh-token",
                "/error"
            ).permitAll()
            .anyRequest().authenticated();
        })
        .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(corsProperties.resolvedAllowedOrigins());

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(
                List.of("*")
        );
        configuration.setExposedHeaders(
                List.of("Authorization", "Content-Type", "Retry-After")
        );
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(
                "/**",
                configuration
        );
        return source;
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }
}
