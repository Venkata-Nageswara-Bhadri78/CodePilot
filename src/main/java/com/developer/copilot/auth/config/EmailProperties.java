package com.developer.copilot.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.mail")
public class EmailProperties {

    @NotBlank
    private String from;

    @NotBlank
    private String senderName;

}
