package com.developer.copilot.community.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.developer.copilot.community.websocket.dto.CommunitySocketError;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommunityStompInboundInterceptor implements ChannelInterceptor, ExecutorChannelInterceptor {

    private final CommunityStompAuthenticator communityStompAuthenticator;
    private final CommunityRealtimeAccess communityRealtimeAccess;
    private final CommunitySubscriptionRegistry subscriptions;
    private final CommunityPresenceService communityPresenceService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = headerAccessor(message);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        try {
            if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
                return connect(message, accessor);
            }
            if (command == StompCommand.SUBSCRIBE) {
                return subscribe(message, accessor);
            }
            if (command == StompCommand.UNSUBSCRIBE) {
                return unsubscribe(message, accessor);
            }
            if (command == StompCommand.SEND) {
                return send(message, accessor);
            }
            if (command == StompCommand.DISCONNECT) {
                communityPresenceService.disconnected(accessor.getSessionId());
            }
            return message;
        } catch (BadCredentialsException | AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
                throw ex;
            }
            reject(accessor, CommunitySocketErrors.from(ex));
            return null;
        }
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, org.springframework.messaging.MessageHandler handler) {
        StompHeaderAccessor accessor = headerAccessor(message);
        if (accessor != null && accessor.getUser() instanceof Authentication authentication) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        return message;
    }

    @Override
    public void afterMessageHandled(
            Message<?> message,
            MessageChannel channel,
            org.springframework.messaging.MessageHandler handler,
            Exception ex) {
        SecurityContextHolder.clearContext();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        communityPresenceService.disconnected(accessor.getSessionId());
    }

    private Message<?> connect(Message<?> message, StompHeaderAccessor accessor) {
        Authentication authentication = communityStompAuthenticator.authenticate(
                accessor.getFirstNativeHeader("Authorization"));
        if (accessor.getSessionId() == null) {
            throw new BadCredentialsException(CommunityStompAuthenticator.UNAUTHORIZED);
        }
        accessor.setLeaveMutable(true);
        accessor.setUser(authentication);
        communityPresenceService.connected(accessor.getSessionId(), authentication);
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private Message<?> subscribe(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (CommunityStompDestinations.userQueue(destination)) {
            return message;
        }
        Authentication authentication = authentication(accessor);
        var topic = CommunityStompDestinations.topic(destination);
        if (authentication == null || topic.isEmpty()) {
            reject(accessor, CommunitySocketErrors.destinationRejected());
            return null;
        }
        try {
            communityRealtimeAccess.requireParticipant(
                    CommunityRealtimeAccess.userId(authentication), topic.get().communityId());
        } catch (RuntimeException ex) {
            reject(accessor, CommunitySocketErrors.from(ex));
            return null;
        }
        if (subscriptions.delivers(accessor.getSessionId(), topic.get().communityId(), topic.get().kind())) {
            return null;
        }
        subscriptions.subscribe(accessor.getSessionId(), topic.get().communityId(), topic.get().kind());
        if (topic.get().kind() == CommunityStompDestinations.TopicKind.PRESENCE) {
            communityPresenceService.sendToUser(
                    authentication,
                    "/queue/presence",
                    communityPresenceService.snapshot(topic.get().communityId()));
        }
        return message;
    }

    private Message<?> unsubscribe(Message<?> message, StompHeaderAccessor accessor) {
        CommunityStompDestinations.topic(accessor.getDestination()).ifPresent(topic ->
                subscriptions.unsubscribe(accessor.getSessionId(), topic.communityId(), topic.kind()));
        return message;
    }

    private Message<?> send(Message<?> message, StompHeaderAccessor accessor) {
        Authentication authentication = authentication(accessor);
        var communityId = CommunityStompDestinations.sendCommunityId(accessor.getDestination());
        if (authentication == null || communityId.isEmpty()) {
            reject(accessor, CommunitySocketErrors.destinationRejected());
            return null;
        }
        try {
            communityRealtimeAccess.requireParticipant(
                    CommunityRealtimeAccess.userId(authentication), communityId.get());
        } catch (RuntimeException ex) {
            reject(accessor, CommunitySocketErrors.from(ex));
            return null;
        }
        return message;
    }

    private void reject(StompHeaderAccessor accessor, CommunitySocketError error) {
        Authentication authentication = authentication(accessor);
        if (authentication != null) {
            communityPresenceService.sendError(authentication, error);
            return;
        }
        throw new MessagingException(error.getMessage());
    }

    private static Authentication authentication(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication) {
            return authentication;
        }
        return null;
    }

    private static StompHeaderAccessor headerAccessor(Message<?> message) {
        StompHeaderAccessor accessor = org.springframework.messaging.support.MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);
        if (accessor != null) {
            return accessor;
        }
        return StompHeaderAccessor.wrap(message);
    }
}
