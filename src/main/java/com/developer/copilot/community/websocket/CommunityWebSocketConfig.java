package com.developer.copilot.community.websocket;

import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.developer.copilot.auth.config.CorsProperties;
import com.developer.copilot.community.config.CommunityWebSocketProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(CommunityWebSocketProperties.class)
@RequiredArgsConstructor
public class CommunityWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final int MESSAGE_SIZE_LIMIT = 64 * 1024;

    private final CorsProperties corsProperties;
    private final CommunityWebSocketProperties communityWebSocketProperties;
    private final CommunityStompInboundInterceptor communityStompInboundInterceptor;
    private final CommunityStompOutboundInterceptor communityStompOutboundInterceptor;
    private ThreadPoolTaskScheduler heartbeatScheduler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(CommunityStompDestinations.ENDPOINT)
                .setAllowedOrigins(corsProperties.resolvedAllowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        long heartbeat = communityWebSocketProperties.getHeartbeatMs();
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {heartbeat, heartbeat})
                .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(communityStompInboundInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(communityStompOutboundInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(15_000);
    }

    @PreDestroy
    public void shutdownHeartbeatScheduler() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
    }

    private ThreadPoolTaskScheduler heartbeatScheduler() {
        if (heartbeatScheduler == null) {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("community-ws-heartbeat-");
            scheduler.initialize();
            heartbeatScheduler = scheduler;
        }
        return heartbeatScheduler;
    }
}
