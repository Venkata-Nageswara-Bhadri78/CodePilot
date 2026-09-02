package com.developer.copilot.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.config.UserProfileProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Turns on JPA auditing (so {@code @CreatedDate}/{@code @LastModifiedDate} fields across all
 * entities get populated) and registers JPA-adjacent configuration-properties beans.
 * {@code InternalApiProperties} is registered in {@link InternalApiSecurityConfig} instead,
 * since it is unrelated to JPA.
 */
@Configuration
@EnableJpaAuditing
@EnableConfigurationProperties({ResumeProperties.class, UserProfileProperties.class})
public class JpaConfig {

}
