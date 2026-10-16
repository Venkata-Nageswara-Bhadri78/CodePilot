package com.developer.copilot.community.websocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.enums.Role;
import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.community.exception.CommunityMembershipRequiredException;
import com.developer.copilot.community.service.CommunityMessages;
import com.developer.copilot.community.websocket.CommunityStompDestinations.TopicKind;
import com.developer.copilot.community.websocket.dto.CommunitySocketError;

@ExtendWith(MockitoExtension.class)
class CommunityStompInboundInterceptorTest {

    @Mock
    private CommunityStompAuthenticator authenticator;
    @Mock
    private CommunityRealtimeAccess access;
    @Mock
    private CommunityPresenceService presence;

    private final CommunitySubscriptionRegistry subscriptions = new CommunitySubscriptionRegistry();
    private CommunityStompInboundInterceptor interceptor;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        interceptor = new CommunityStompInboundInterceptor(authenticator, access, subscriptions, presence);
        User user = new User();
        user.setId(7L);
        user.setEmail("ada@example.com");
        user.setUsername("ada");
        user.setRole(Role.USER);
        authentication = new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, null);
    }

    @Test
    void connect_setsTheAuthenticatedUser() {
        when(authenticator.authenticate("Bearer token")).thenReturn(authentication);
        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer token"), null);

        assertSame(authentication, StompHeaderAccessor.wrap(result).getUser());
        verify(presence).connected("sess-1", authentication);
    }

    @Test
    void connect_withoutAToken_isRejected() {
        when(authenticator.authenticate(null)).thenThrow(new BadCredentialsException("Unauthorized."));
        assertThrows(BadCredentialsException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null), null));
        verify(presence, never()).connected(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void member_canSubscribeAndSend() {
        Message<?> subscribed = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/communities/10/messages", authentication), null);
        Message<?> sent = interceptor.preSend(
                frame(StompCommand.SEND, "/app/communities/10/messages", authentication), null);

        assertSame(subscribed.getHeaders(), subscribed.getHeaders());
        org.junit.jupiter.api.Assertions.assertNotNull(subscribed);
        org.junit.jupiter.api.Assertions.assertNotNull(sent);
        assertTrueSubscribed();
        verify(access, org.mockito.Mockito.atLeastOnce()).requireParticipant(7L, 10L);
    }

    @Test
    void nonMember_cannotSubscribeOrSend() {
        doThrow(new CommunityMembershipRequiredException(CommunityMessages.MEMBERSHIP_REQUIRED))
                .when(access).requireParticipant(7L, 10L);

        assertNull(interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/communities/10/messages", authentication), null));
        assertNull(interceptor.preSend(
                frame(StompCommand.SEND, "/app/communities/10/messages", authentication), null));
        verify(presence, org.mockito.Mockito.atLeastOnce()).sendError(
                org.mockito.ArgumentMatchers.eq(authentication), org.mockito.ArgumentMatchers.any(CommunitySocketError.class));
        assertFalse(subscriptions.delivers("sess-1", 10L, TopicKind.MESSAGES));
    }

    @Test
    void client_cannotSendStraightToTheBroker() {
        assertNull(interceptor.preSend(
                frame(StompCommand.SEND, "/topic/communities/10/messages", authentication), null));
        verify(access, never()).requireParticipant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(presence).sendError(org.mockito.ArgumentMatchers.eq(authentication), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void suppliedUserId_isNotReadFromTheDestination() {
        assertNull(interceptor.preSend(
                frame(StompCommand.SEND, "/app/communities/10/messages/7", authentication), null));
        verify(access, never()).requireParticipant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void assertTrueSubscribed() {
        org.junit.jupiter.api.Assertions.assertTrue(subscriptions.delivers("sess-1", 10L, TopicKind.MESSAGES));
    }

    private static Message<byte[]> frame(StompCommand command, String destination, Object authorizationOrUser) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("sess-1");
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorizationOrUser instanceof String header) {
            accessor.setNativeHeader("Authorization", header);
        } else if (authorizationOrUser instanceof Authentication authentication) {
            accessor.setUser(authentication);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
