package com.developer.copilot.community.websocket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.developer.copilot.community.websocket.CommunityStompDestinations.TopicKind;

/**
 * Which community topics a live socket is allowed to receive. Leaving removes that community.
 */
@Component
public class CommunitySubscriptionRegistry {

    private final ConcurrentHashMap<String, Long> userBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> topicsBySession = new ConcurrentHashMap<>();

    public void bind(String sessionId, long userId) {
        userBySession.put(sessionId, userId);
        topicsBySession.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet());
    }

    public void subscribe(String sessionId, long communityId, TopicKind kind) {
        topicsBySession.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(key(communityId, kind));
    }

    public void unsubscribe(String sessionId, long communityId, TopicKind kind) {
        Set<String> topics = topicsBySession.get(sessionId);
        if (topics != null) {
            topics.remove(key(communityId, kind));
        }
    }

    public boolean delivers(String sessionId, long communityId, TopicKind kind) {
        Set<String> topics = topicsBySession.get(sessionId);
        return topics != null && topics.contains(key(communityId, kind));
    }

    public void revoke(long userId, long communityId) {
        userBySession.forEach((sessionId, owner) -> {
            if (owner == userId) {
                Set<String> topics = topicsBySession.get(sessionId);
                if (topics != null) {
                    topics.remove(key(communityId, TopicKind.MESSAGES));
                    topics.remove(key(communityId, TopicKind.PRESENCE));
                }
            }
        });
    }

    public void clear(String sessionId) {
        userBySession.remove(sessionId);
        topicsBySession.remove(sessionId);
    }

    private static String key(long communityId, TopicKind kind) {
        return communityId + ":" + kind.name();
    }
}
