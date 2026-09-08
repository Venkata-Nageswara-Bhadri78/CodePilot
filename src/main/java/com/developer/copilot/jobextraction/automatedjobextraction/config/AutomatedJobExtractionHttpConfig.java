package com.developer.copilot.jobextraction.automatedjobextraction.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.developer.copilot.jobextraction.automatedjobextraction.fetch.JdkJobPageHttpClient;
import com.developer.copilot.jobextraction.automatedjobextraction.fetch.JobPageHttpClient;

@Configuration
@EnableConfigurationProperties(AutomatedJobExtractionHttpProperties.class)
public class AutomatedJobExtractionHttpConfig {

    @Bean
    JobPageHttpClient jobPageHttpClient(AutomatedJobExtractionHttpProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())))
                .build();
        return new JdkJobPageHttpClient(httpClient);
    }
}
