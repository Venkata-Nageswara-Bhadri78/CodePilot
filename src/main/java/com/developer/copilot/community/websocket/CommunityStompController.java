package com.developer.copilot.community.websocket;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import com.developer.copilot.community.service.CommunityMessageService;
import com.developer.copilot.community.websocket.dto.CommunitySendPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CommunityStompController {

    private final CommunityMessageService communityMessageService;
    private final CommunityPresenceService communityPresenceService;

    @MessageMapping("/communities/{communityId}/messages")
    public void send(
            @DestinationVariable Long communityId,
            CommunitySendPayload payload,
            Authentication authentication) {
        communityMessageService.send(communityId, payload == null ? null : payload.getContent());
    }

    @MessageExceptionHandler
    public void onSendFailure(Throwable failure, Authentication authentication, StompHeaderAccessor accessor) {
        Long userId = CommunityRealtimeAccess.userId(authentication);
        log.info("Community socket send rejected userId={} type={}", userId, failure.getClass().getSimpleName());
        communityPresenceService.sendError(authentication, CommunitySocketErrors.from(failure));
    }
}
