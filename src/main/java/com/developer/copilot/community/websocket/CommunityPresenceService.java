package com.developer.copilot.community.websocket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.community.config.CommunityWebSocketProperties;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.repository.CommunityMembershipRepository;
import com.developer.copilot.community.service.CommunityMembershipChanged;
import com.developer.copilot.community.websocket.dto.CommunityMembershipSocketEvent;
import com.developer.copilot.community.websocket.dto.CommunityPresenceEvent;
import com.developer.copilot.community.websocket.dto.CommunityPresenceSnapshot;
import com.developer.copilot.community.websocket.dto.CommunitySocketError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityPresenceService {

    private final CommunityPresenceTracker tracker;
    private final CommunitySubscriptionRegistry subscriptions;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplate;
    private final ObjectProvider<SimpUserRegistry> simpUserRegistry;
    private final CommunityWebSocketProperties communityWebSocketProperties;

    public void connected(String sessionId, Authentication authentication) {
        User user = CommunityRealtimeAccess.user(authentication);
        if (sessionId == null || user == null || user.getId() == null) {
            return;
        }
        boolean first = tracker.connected(
                sessionId, user.getId(), user.getUsername(), user.getFullName(), Instant.now());
        subscriptions.bind(sessionId, user.getId());
        if (first) {
            log.info("Community presence online userId={}", user.getId());
            announce(user.getId(), user.getUsername(), user.getFullName(), CommunityPresenceEvent.ONLINE);
        }
    }

    public void disconnected(String sessionId) {
        if (sessionId == null) {
            return;
        }
        subscriptions.clear(sessionId);
        tracker.disconnected(sessionId).ifPresent(change -> {
            if (change.wentOffline()) {
                log.info("Community presence offline userId={}", change.person().userId());
                announce(
                        change.person().userId(),
                        change.person().username(),
                        change.person().fullName(),
                        CommunityPresenceEvent.OFFLINE);
            }
        });
    }

    public CommunityPresenceSnapshot snapshot(long communityId) {
        List<CommunityPresenceEvent> online = new ArrayList<>();
        for (CommunityMembership membership : communityMembershipRepository
                .findByCommunityIdAndMembershipStatusOrderByJoinedAtAscIdAsc(
                        communityId, MembershipStatus.ACTIVE)) {
            if (membership.getUser() == null || membership.getUser().getId() == null) {
                continue;
            }
            tracker.person(membership.getUser().getId()).ifPresent(person -> online.add(CommunityPresenceEvent.builder()
                    .communityId(communityId)
                    .userId(person.userId())
                    .username(person.username())
                    .fullName(person.fullName())
                    .status(CommunityPresenceEvent.ONLINE)
                    .build()));
        }
        return CommunityPresenceSnapshot.builder().communityId(communityId).online(online).build();
    }

    public void sendToUser(Authentication authentication, String queue, Object payload) {
        SimpMessagingTemplate template = messagingTemplate.getIfAvailable();
        if (authentication == null || authentication.getName() == null || template == null) {
            return;
        }
        template.convertAndSendToUser(authentication.getName(), queue, payload);
    }

    public void sendError(Authentication authentication, CommunitySocketError error) {
        sendToUser(authentication, "/queue/errors", error);
    }

    @EventListener
    public void onMembershipChanged(CommunityMembershipChanged event) {
        if (event.userId() == null || event.communityId() == null || event.status() == null) {
            return;
        }
        if (event.status() == MembershipStatus.LEFT) {
            subscriptions.revoke(event.userId(), event.communityId());
            tracker.person(event.userId()).ifPresent(person -> publish(
                    event.communityId(), person, CommunityPresenceEvent.OFFLINE));
        } else if (event.status() == MembershipStatus.ACTIVE && tracker.online(event.userId())) {
            tracker.person(event.userId()).ifPresent(person -> publish(
                    event.communityId(), person, CommunityPresenceEvent.ONLINE));
        }
        tracker.person(event.userId()).ifPresent(person -> {
            Authentication authentication = authenticationFor(person.userId());
            if (authentication != null) {
                sendToUser(authentication, "/queue/membership", CommunityMembershipSocketEvent.builder()
                        .communityId(event.communityId())
                        .userId(event.userId())
                        .membership(event.status() == MembershipStatus.ACTIVE ? "MEMBER" : "NOT_A_MEMBER")
                        .build());
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.community.ws.presence-sweep-ms:15000}")
    public void sweepForgottenSessions() {
        Instant cutoff = Instant.now().minusMillis(communityWebSocketProperties.getPresenceSweepMs());
        for (String sessionId : tracker.sessionIds()) {
            Instant connectedAt = tracker.connectedAt(sessionId).orElse(Instant.EPOCH);
            if (connectedAt.isAfter(cutoff)) {
                continue;
            }
            if (!registryHas(sessionId)) {
                disconnected(sessionId);
            }
        }
    }

    private void announce(long userId, String username, String fullName, String status) {
        CommunityPresenceTracker.Person person = new CommunityPresenceTracker.Person(userId, username, fullName);
        for (CommunityMembership membership : communityMembershipRepository
                .findByUserIdAndMembershipStatusOrderByJoinedAtDescIdDesc(userId, MembershipStatus.ACTIVE)) {
            if (membership.getCommunity() != null && membership.getCommunity().getId() != null) {
                publish(membership.getCommunity().getId(), person, status);
            }
        }
    }

    private void publish(long communityId, CommunityPresenceTracker.Person person, String status) {
        SimpMessagingTemplate template = messagingTemplate.getIfAvailable();
        if (template == null) {
            return;
        }
        template.convertAndSend(
                CommunityStompDestinations.presenceTopic(communityId),
                CommunityPresenceEvent.builder()
                        .communityId(communityId)
                        .userId(person.userId())
                        .username(person.username())
                        .fullName(person.fullName())
                        .status(status)
                        .build());
    }

    private boolean registryHas(String sessionId) {
        SimpUserRegistry registry = simpUserRegistry.getIfAvailable();
        if (registry == null) {
            return true;
        }
        for (SimpUser user : registry.getUsers()) {
            for (SimpSession session : user.getSessions()) {
                if (sessionId.equals(session.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private Authentication authenticationFor(long userId) {
        SimpUserRegistry registry = simpUserRegistry.getIfAvailable();
        if (registry == null) {
            return null;
        }
        for (SimpUser user : registry.getUsers()) {
            if (user.getPrincipal() instanceof Authentication authentication
                    && Long.valueOf(userId).equals(CommunityRealtimeAccess.userId(authentication))) {
                return authentication;
            }
        }
        return null;
    }
}
