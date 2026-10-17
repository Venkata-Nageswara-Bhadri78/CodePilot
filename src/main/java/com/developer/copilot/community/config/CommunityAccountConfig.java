package com.developer.copilot.community.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CommunityAccountProperties.class)
public class CommunityAccountConfig {
}
