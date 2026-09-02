package com.developer.copilot.user.config;

import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

/**
 * Aligns the servlet multipart cap with {@code resume.max-file-size-mb} so a 2–5MB PDF
 * is not rejected by Spring Boot's 1MB default before {@link com.developer.copilot.user.service.impl.UserServiceImpl}
 * can validate it.
 */
@Configuration
public class ResumeMultipartConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement(ResumeProperties resumeProperties) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        DataSize maxFile = DataSize.ofMegabytes(Math.max(1, resumeProperties.getMaxFileSizeMb()));
        factory.setMaxFileSize(maxFile);
        factory.setMaxRequestSize(DataSize.ofBytes(maxFile.toBytes() + 512 * 1024));
        return factory.createMultipartConfig();
    }
}
