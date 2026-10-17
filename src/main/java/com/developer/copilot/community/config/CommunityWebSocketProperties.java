package com.developer.copilot.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.community.ws")
public class CommunityWebSocketProperties {

    /**
     * STOMP heartbeat interval in milliseconds. The server sends heartbeats at this pace and
     * expects the client to do the same. A silent socket is closed by the broker, then presence
     * drops that session.
     */
    @Min(value = 1000, message = "app.community.ws.heartbeat-ms must be at least 1000.")
    @Max(value = 120_000, message = "app.community.ws.heartbeat-ms cannot exceed 120000.")
    private long heartbeatMs = 10_000;

    /**
     * How often to drop presence for a socket the broker has already forgotten.
     */
    @Min(value = 1000, message = "app.community.ws.presence-sweep-ms must be at least 1000.")
    @Max(value = 300_000, message = "app.community.ws.presence-sweep-ms cannot exceed 300000.")
    private long presenceSweepMs = 15_000;
}
