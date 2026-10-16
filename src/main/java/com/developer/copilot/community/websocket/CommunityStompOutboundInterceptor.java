package com.developer.copilot.community.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Drops a community broadcast when that socket is no longer allowed to see the community.
 */
@Component
@RequiredArgsConstructor
public class CommunityStompOutboundInterceptor implements ChannelInterceptor {

    private final CommunitySubscriptionRegistry subscriptions;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        var topic = CommunityStompDestinations.topic(accessor.getDestination());
        if (topic.isEmpty()) {
            return message;
        }
        String sessionId = accessor.getSessionId();
        if (sessionId == null
                || !subscriptions.delivers(sessionId, topic.get().communityId(), topic.get().kind())) {
            return null;
        }
        return message;
    }
}
