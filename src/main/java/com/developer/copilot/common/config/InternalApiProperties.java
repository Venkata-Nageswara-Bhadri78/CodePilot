package com.developer.copilot.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "internal.api")
public class InternalApiProperties {

    /**
     * Shared secret that calling services must send in the internal API key header.
     */
    private String key;

    /**
     * When disabled, internal endpoints are reachable without the shared secret.
     * Intended for local development only.
     */
    private boolean enabled = true;

    /**
     * Header carrying the shared secret.
     */
    private String headerName = "X-Internal-Api-Key";

    /**
     * Path prefix that identifies internal service-to-service endpoints.
     */
    private String pathPrefix = "/api/v1/internal";
}
