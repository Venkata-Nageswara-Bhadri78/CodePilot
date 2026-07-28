package com.developer.copilot.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "resume")
public class ResumeProperties {

    /**
     * Maximum resumes allowed per user.
     */
    private int maxResumeCount = 10;

    /**
     * Maximum file size in MB.
     */
    private int maxFileSizeMb = 5;

}