package com.developer.copilot.community.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.developer.copilot.community.websocket.CommunityStompDestinations.TopicKind;

class CommunityStompDestinationsTest {

    @Test
    void acceptsOnlyTheDocumentedSendAndTopics() {
        assertEquals(10L, CommunityStompDestinations.sendCommunityId("/app/communities/10/messages").orElseThrow());
        CommunityStompDestinations.Topic messages = CommunityStompDestinations.topic("/topic/communities/10/messages").orElseThrow();
        CommunityStompDestinations.Topic presence = CommunityStompDestinations.topic("/topic/communities/10/presence").orElseThrow();
        assertEquals(TopicKind.MESSAGES, messages.kind());
        assertEquals(TopicKind.PRESENCE, presence.kind());
        assertTrue(CommunityStompDestinations.userQueue("/user/queue/errors"));
        assertTrue(CommunityStompDestinations.userQueue("/user/queue/presence"));
        assertTrue(CommunityStompDestinations.userQueue("/user/queue/membership"));
    }

    @Test
    void rejectsBrokerSendsAndGuessedDestinations() {
        assertTrue(CommunityStompDestinations.sendCommunityId("/topic/communities/10/messages").isEmpty());
        assertTrue(CommunityStompDestinations.sendCommunityId("/app/communities/10/presence").isEmpty());
        assertTrue(CommunityStompDestinations.sendCommunityId("/app/communities/0/messages").isEmpty());
        assertTrue(CommunityStompDestinations.topic("/topic/communities/10/messages/extra").isEmpty());
        assertTrue(CommunityStompDestinations.topic("/topic/other").isEmpty());
        assertFalse(CommunityStompDestinations.userQueue("/user/queue/secrets"));
    }
}
