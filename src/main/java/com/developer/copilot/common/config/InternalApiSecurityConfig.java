package com.developer.copilot.common.config;

import com.developer.copilot.common.security.internal.InternalApiKeyFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires up the second, independent authentication layer for {@code /api/v1/internal/**}
 * endpoints (see {@link InternalApiKeyFilter}).
 * <p>
 * Note for anyone reading the main Spring Security {@code SecurityFilterChain} (in the
 * {@code auth} service): that chain only validates the JWT. Requests under the internal
 * path prefix additionally require the shared secret enforced by the filter registered
 * below, which runs afterwards and is intentionally outside Spring Security's own chain.
 */
@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiSecurityConfig {

    /**
     * After {@code springSecurityFilterChain} (-100) so JWT is present; before internal
     * rate-limit (-70) so 401s are not counted. Scoped to the internal path prefix so no
     * public endpoint ever requires the service key.
     */
    private static final int AFTER_JWT = -90;

    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilterRegistration(
            InternalApiProperties internalApiProperties,
            ObjectMapper objectMapper,
            Environment environment) {

        FilterRegistrationBean<InternalApiKeyFilter> registration = new FilterRegistrationBean<>(
                new InternalApiKeyFilter(internalApiProperties, objectMapper, environment));

        registration.setName("internalApiKeyFilter");
        String prefix = normalizePrefix(internalApiProperties.getPathPrefix());
        registration.addUrlPatterns(prefix, prefix + "/*");
        registration.setOrder(AFTER_JWT);

        return registration;
    }

    private static String normalizePrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            return "/api/v1/internal";
        }
        String prefix = pathPrefix.trim();
        while (prefix.endsWith("/") && prefix.length() > 1) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
