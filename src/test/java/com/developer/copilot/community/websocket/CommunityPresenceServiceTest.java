package com.developer.copilot.community.websocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.community.config.CommunityWebSocketProperties;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.repository.CommunityMembershipRepository;

@ExtendWith(MockitoExtension.class)
class CommunityPresenceServiceTest {

    @Mock
    private CommunityMembershipRepository communityMembershipRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ObjectProvider<SimpUserRegistry> simpUserRegistry;
    @Mock
    private SimpUserRegistry registry;
    @Mock
    private SimpUser simpUser;
    @Mock
    private SimpSession simpSession;

    private final CommunityPresenceTracker tracker = new CommunityPresenceTracker();
    private final CommunitySubscriptionRegistry subscriptions = new CommunitySubscriptionRegistry();
    private CommunityPresenceService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new CommunityPresenceService(
                tracker,
                subscriptions,
                communityMembershipRepository,
                messagingTemplate,
                simpUserRegistry,
                new CommunityWebSocketProperties());
        user = new User();
        user.setId(7L);
        user.setEmail("ada@example.com");
        user.setUsername("ada");
        user.setFullName("Ada Lovelace");
        Community community = new Community();
        community.setId(10L);
        CommunityMembership membership = new CommunityMembership();
        membership.setCommunity(community);
        membership.setUser(user);
        membership.setMembershipStatus(MembershipStatus.ACTIVE);
        lenient().when(communityMembershipRepository.findByUserIdAndMembershipStatusOrderByJoinedAtDescIdDesc(
                7L, MembershipStatus.ACTIVE)).thenReturn(List.of(membership));
    }

    @Test
    void secondTab_doesNotBroadcastOnlineAgain_andOneCloseStaysOnline() {
        var authentication = new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, null);
        service.connected("tab-a", authentication);
        service.connected("tab-b", authentication);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/communities/10/presence"), any(Object.class));

        service.disconnected("tab-a");
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/communities/10/presence"), any(Object.class));
        assertTrue(tracker.online(7L));

        service.disconnected("tab-b");
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/communities/10/presence"), any(Object.class));
        assertFalse(tracker.online(7L));
    }

    @Test
    void sweep_dropsASessionTheBrokerHasForgotten() {
        tracker.connected("old", 7L, "ada", "Ada Lovelace", Instant.now().minusSeconds(60));
        when(simpUserRegistry.getIfAvailable()).thenReturn(registry);
        when(registry.getUsers()).thenReturn(Set.of());

        service.sweepForgottenSessions();

        assertFalse(tracker.online(7L));
        verify(messagingTemplate).convertAndSend(eq("/topic/communities/10/presence"), any(Object.class));
    }

    @Test
    void sweep_keepsASessionTheBrokerStillHas() {
        tracker.connected("live", 7L, "ada", "Ada Lovelace", Instant.now().minusSeconds(60));
        when(simpUserRegistry.getIfAvailable()).thenReturn(registry);
        when(registry.getUsers()).thenReturn(Set.of(simpUser));
        when(simpUser.getSessions()).thenReturn(Set.of(simpSession));
        when(simpSession.getId()).thenReturn("live");

        service.sweepForgottenSessions();

        assertTrue(tracker.online(7L));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
