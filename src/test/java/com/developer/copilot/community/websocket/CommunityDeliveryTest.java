package com.developer.copilot.community.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.developer.copilot.community.dto.response.CommunityMessageResponse;
import com.developer.copilot.community.service.CommunityMessagePersisted;
import com.developer.copilot.community.websocket.CommunityStompDestinations.TopicKind;

@ExtendWith(MockitoExtension.class)
class CommunityDeliveryTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    private final CommunitySubscriptionRegistry subscriptions = new CommunitySubscriptionRegistry();

    @Test
    void persistedMessage_isBroadcastWithItsServerId() {
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);
        CommunityMessageBroadcast broadcast = new CommunityMessageBroadcast(messagingTemplateProvider);
        CommunityMessageResponse saved = CommunityMessageResponse.builder().id(42L).communityId(10L).content("Hi").build();

        broadcast.onPersisted(new CommunityMessagePersisted(saved));

        ArgumentCaptor<CommunityMessageResponse> payload = ArgumentCaptor.forClass(CommunityMessageResponse.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/communities/10/messages"), payload.capture());
        assertEquals(42L, payload.getValue().getId());
        assertEquals("Hi", payload.getValue().getContent());
    }

    @Test
    void unsavedMessage_isNotBroadcast() {
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);
        CommunityMessageBroadcast broadcast = new CommunityMessageBroadcast(messagingTemplateProvider);
        broadcast.onPersisted(new CommunityMessagePersisted(CommunityMessageResponse.builder().build()));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void departedSubscription_doesNotReceiveTheTopic() {
        CommunityStompOutboundInterceptor outbound = new CommunityStompOutboundInterceptor(subscriptions);
        subscriptions.bind("sess-1", 7L);
        subscriptions.subscribe("sess-1", 10L, TopicKind.MESSAGES);
        Message<byte[]> message = topicFrame("sess-1");
        assertSame(message, outbound.preSend(message, null));

        subscriptions.revoke(7L, 10L);
        assertNull(outbound.preSend(topicFrame("sess-1"), null));
    }

    private static Message<byte[]> topicFrame(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination("/topic/communities/10/messages");
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
