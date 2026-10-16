package com.developer.copilot.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.community")
public class CommunityAccountProperties {

    /**
     * Terms version a user must accept before the community account is current.
     * Changing this value makes every previously accepted version stale.
     */
    @NotBlank(message = "app.community.terms-version must not be blank.")
    @Size(max = 64, message = "app.community.terms-version cannot exceed 64 characters.")
    private String termsVersion = "1";

    public String currentTermsVersion() {
        return termsVersion.trim();
    }
}
