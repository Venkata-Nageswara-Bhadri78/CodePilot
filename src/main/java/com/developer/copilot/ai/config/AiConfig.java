package com.developer.copilot.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI configuration initializing the fluent {@link ChatClient}.
 * <p>
 * Applies {@link AiProperties#getDefaultModel()} as the default model so reported
 * metadata matches the model actually invoked.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, AiProperties aiProperties) {
        return chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(aiProperties.getDefaultModel())
                        .maxTokens(aiProperties.getMaxCompletionTokens()))
                .build();
    }
}
