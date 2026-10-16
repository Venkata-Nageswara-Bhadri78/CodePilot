package com.developer.copilot.community.websocket;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory sockets for one application process. A person is online while at least one socket remains.
 */
@Component
public class CommunityPresenceTracker {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    public record Person(long userId, String username, String fullName) {
    }

    public record Disconnect(Person person, boolean wentOffline) {
    }

    /**
     * @return true when this socket is the person's first, so callers should announce online once
     */
    public boolean connected(String sessionId, long userId, String username, String fullName, Instant connectedAt) {
        sessions.put(sessionId, new Session(sessionId, userId, username, fullName, connectedAt));
        while (true) {
            Set<String> open = sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
            synchronized (open) {
                if (sessionsByUser.get(userId) != open) {
                    continue;
                }
                open.add(sessionId);
                return open.size() == 1;
            }
        }
    }

    public Optional<Disconnect> disconnected(String sessionId) {
        Session removed = sessions.remove(sessionId);
        if (removed == null) {
            return Optional.empty();
        }
        Set<String> open = sessionsByUser.get(removed.userId);
        if (open == null) {
            return Optional.of(new Disconnect(new Person(removed.userId, removed.username, removed.fullName), true));
        }
        synchronized (open) {
            open.remove(sessionId);
            boolean wentOffline = open.isEmpty();
            if (wentOffline) {
                sessionsByUser.remove(removed.userId, open);
            }
            return Optional.of(new Disconnect(new Person(removed.userId, removed.username, removed.fullName), wentOffline));
        }
    }

    public Optional<Person> person(long userId) {
        Set<String> open = sessionsByUser.get(userId);
        if (open == null) {
            return Optional.empty();
        }
        synchronized (open) {
            for (String sessionId : open) {
                Session session = sessions.get(sessionId);
                if (session != null) {
                    return Optional.of(new Person(session.userId, session.username, session.fullName));
                }
            }
        }
        return Optional.empty();
    }

    public boolean online(long userId) {
        Set<String> open = sessionsByUser.get(userId);
        return open != null && !open.isEmpty();
    }

    public Set<String> sessionIds() {
        return Set.copyOf(sessions.keySet());
    }

    public Optional<Instant> connectedAt(String sessionId) {
        Session session = sessions.get(sessionId);
        return session == null ? Optional.empty() : Optional.of(session.connectedAt);
    }

    private record Session(String sessionId, long userId, String username, String fullName, Instant connectedAt) {
    }
}
