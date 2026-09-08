package com.developer.copilot.auth.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.developer.copilot.auth.jwt.JwtAuthenticationFilter;
import com.developer.copilot.auth.ratelimit.config.AuthRateLimitConfig;
import com.developer.copilot.auth.ratelimit.filter.AuthRateLimitFilter;
import com.developer.copilot.auth.security.AuthClientAuthorities;

import lombok.RequiredArgsConstructor;

@Configuration
@Import({JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class, AuthRateLimitConfig.class})
@EnableConfigurationProperties({CorsProperties.class, AuthProperties.class, ExtensionProperties.class})
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

    private static final String[] JOB_EXTRACTION_PATHS = {
            "/api/v1/job-extraction/**",
            "/api/v1/automated-job-extraction/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;
    private final ExtensionProperties extensionProperties;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final JsonAccessDeniedHandler jsonAccessDeniedHandler;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                .accessDeniedHandler(jsonAccessDeniedHandler))
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
            .requestMatchers(JOB_EXTRACTION_PATHS).authenticated()
            .anyRequest().access(webFrontendOnly());
        })
        .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        List<String> allowedOrigins = new ArrayList<>(corsProperties.resolvedAllowedOrigins());
        extensionProperties.resolvedOrigin().ifPresent(allowedOrigins::add);
        configuration.setAllowedOrigins(allowedOrigins);

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

    /**
     * Web frontend tokens (no {@code CLIENT_BROWSER_EXTENSION} authority) keep existing access.
     * Extension tokens are authenticated but must not reach any other service.
     */
    private static AuthorizationManager<RequestAuthorizationContext> webFrontendOnly() {
        return AuthorizationManagers.allOf(
                AuthenticatedAuthorizationManager.authenticated(),
                AuthorizationManagers.not(
                        AuthorityAuthorizationManager.hasAuthority(AuthClientAuthorities.BROWSER_EXTENSION)));
    }
}
