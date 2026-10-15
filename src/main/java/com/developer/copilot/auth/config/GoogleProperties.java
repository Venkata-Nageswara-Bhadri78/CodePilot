package com.developer.copilot.auth.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.google")
public class GoogleProperties {

    /**
     * GIS web client IDs. The ID token {@code aud} claim must match one of them.
     */
    private List<String> clientIds = new ArrayList<>();

    /**
     * Optional Workspace hosted-domain ({@code hd}) restriction. Blank disables the check.
     */
    private String hostedDomain = "";

    public List<String> resolvedClientIds() {
        if (clientIds == null || clientIds.isEmpty()) {
            return List.of();
        }
        return clientIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .toList();
    }

    public String resolvedHostedDomain() {
        if (hostedDomain == null || hostedDomain.isBlank()) {
            return "";
        }
        return hostedDomain.trim().toLowerCase(Locale.ROOT);
    }
}
