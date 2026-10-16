package com.developer.copilot.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /**
     * Alternative posting URLs allowed on one create, in addition to the primary URL.
     */
    @Min(value = 1, message = "app.community.max-url-aliases must be at least 1.")
    @Max(value = 20, message = "app.community.max-url-aliases cannot exceed 20.")
    private int maxUrlAliases = 8;

    /**
     * Maximum message length in Unicode code points. Emoji count as one character.
     */
    @Min(value = 1, message = "app.community.max-message-length must be at least 1.")
    @Max(value = 10_000, message = "app.community.max-message-length cannot exceed 10000.")
    private int maxMessageLength = 4000;

    /**
     * Messages returned when a history request omits limit.
     */
    @Min(value = 1, message = "app.community.message-page-size must be at least 1.")
    @Max(value = 100, message = "app.community.message-page-size cannot exceed 100.")
    private int messagePageSize = 30;

    /**
     * Largest page a client may request. History never returns the whole community.
     */
    @Min(value = 1, message = "app.community.max-message-page-size must be at least 1.")
    @Max(value = 100, message = "app.community.max-message-page-size cannot exceed 100.")
    private int maxMessagePageSize = 50;

    public String currentTermsVersion() {
        return termsVersion.trim();
    }

    @AssertTrue(message = "app.community.message-page-size cannot exceed app.community.max-message-page-size.")
    public boolean isMessagePageSizeWithinMax() {
        return messagePageSize <= maxMessagePageSize;
    }
}
