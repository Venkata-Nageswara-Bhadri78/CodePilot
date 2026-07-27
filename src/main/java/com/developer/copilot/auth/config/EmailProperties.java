package com.developer.copilot.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.mail")
public class EmailProperties {

    private String from;

    private String senderName;

}