package com.developer.copilot.auth.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "cors")
@Getter
@Setter
public class CorsProperties {

    private List<String> allowedOrigins = List.of(
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:3000",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5174",
            "http://127.0.0.1:3000"
    );

    /**
     * Origins that are safe with {@code allowCredentials=true}. A wildcard is dropped so a
     * misconfigured {@code cors.allowed-origins=*} cannot pair with credentialed CORS.
     */
    public List<String> resolvedAllowedOrigins() {
        if (allowedOrigins == null) {
            return List.of();
        }
        return allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank() && !"*".equals(origin.trim()))
                .toList();
    }
}
