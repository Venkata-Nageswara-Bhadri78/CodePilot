package com.developer.copilot.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "user.profile")
public class UserProfileProperties {

    /**
     * Maximum child rows (experiences, educations, projects, additional-info, links)
     * allowed per collection on one profile.
     */
    private int maxChildItems = 20;
}
