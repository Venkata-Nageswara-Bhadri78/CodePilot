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
     * Previous secret accepted during rotation. Remove after callers have switched to
     * {@link #key}. Compared with the same constant-time check; never logged.
     */
    private String previousKey;

    /**
     * When disabled, the shared-secret check is skipped only on {@code local}/{@code dev}.
     * Any other profile still rejects the request (fail closed).
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
