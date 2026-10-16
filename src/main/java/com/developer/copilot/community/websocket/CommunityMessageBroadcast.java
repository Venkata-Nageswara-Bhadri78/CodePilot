package com.developer.copilot.community.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.developer.copilot.community.dto.response.CommunityMessageResponse;
import com.developer.copilot.community.service.CommunityMessagePersisted;

import lombok.RequiredArgsConstructor;

/**
 * Sends a message that was already stored. The event is published only after the save commits.
 */
@Component
@RequiredArgsConstructor
public class CommunityMessageBroadcast {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onPersisted(CommunityMessagePersisted event) {
        CommunityMessageResponse message = event.message();
        if (message == null || message.getCommunityId() == null || message.getId() == null) {
            return;
        }
        messagingTemplate.convertAndSend(CommunityStompDestinations.messagesTopic(message.getCommunityId()), message);
    }
}
