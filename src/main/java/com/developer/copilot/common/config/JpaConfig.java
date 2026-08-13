package com.developer.copilot.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import com.developer.copilot.user.config.ResumeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableJpaAuditing
@EnableConfigurationProperties(ResumeProperties.class)
public class JpaConfig {
    
}
