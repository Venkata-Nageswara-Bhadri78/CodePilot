package com.developer.copilot.community.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class CommunityPresenceTrackerTest {

    private final CommunityPresenceTracker tracker = new CommunityPresenceTracker();

    @Test
    void oneTabClosing_leavesTheUserOnlineWhileAnotherTabRemains() {
        assertTrue(tracker.connected("tab-a", 7L, "ada", "Ada Lovelace", Instant.now()));
        assertFalse(tracker.connected("tab-b", 7L, "ada", "Ada Lovelace", Instant.now()));

        CommunityPresenceTracker.Disconnect first = tracker.disconnected("tab-a").orElseThrow();
        assertFalse(first.wentOffline());
        assertTrue(tracker.online(7L));

        CommunityPresenceTracker.Disconnect last = tracker.disconnected("tab-b").orElseThrow();
        assertTrue(last.wentOffline());
        assertEquals(7L, last.person().userId());
        assertFalse(tracker.online(7L));
    }

    @Test
    void disconnectTwice_isSafe() {
        tracker.connected("tab-a", 7L, "ada", "Ada", Instant.now());
        tracker.disconnected("tab-a");
        assertTrue(tracker.disconnected("tab-a").isEmpty());
        assertFalse(tracker.online(7L));
    }

    @Test
    void forgottenSession_canBeRemoved() {
        Instant old = Instant.now().minusSeconds(60);
        tracker.connected("stale", 7L, "ada", "Ada", old);
        assertTrue(tracker.connectedAt("stale").orElseThrow().isBefore(Instant.now().minusSeconds(30)));
        tracker.disconnected("stale");
        assertFalse(tracker.online(7L));
        assertTrue(tracker.sessionIds().isEmpty());
    }
}
