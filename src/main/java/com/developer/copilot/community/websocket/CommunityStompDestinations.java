package com.developer.copilot.community.websocket;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The only STOMP destinations the community socket accepts.
 */
public final class CommunityStompDestinations {

    public static final String ENDPOINT = "/ws/community";
    public static final String USER_ERRORS = "/user/queue/errors";
    public static final String USER_PRESENCE = "/user/queue/presence";
    public static final String USER_MEMBERSHIP = "/user/queue/membership";

    private static final Pattern SEND = Pattern.compile("^/app/communities/([1-9]\\d*)/messages$");
    private static final Pattern TOPIC = Pattern.compile("^/topic/communities/([1-9]\\d*)/(messages|presence)$");

    private CommunityStompDestinations() {
    }

    public enum TopicKind {
        MESSAGES,
        PRESENCE
    }

    public record Topic(long communityId, TopicKind kind) {
    }

    public static String messagesTopic(long communityId) {
        return "/topic/communities/" + communityId + "/messages";
    }

    public static String presenceTopic(long communityId) {
        return "/topic/communities/" + communityId + "/presence";
    }

    public static Optional<Long> sendCommunityId(String destination) {
        return matchId(SEND, destination);
    }

    public static Optional<Topic> topic(String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Matcher matcher = TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long communityId = Long.parseLong(matcher.group(1));
        TopicKind kind = "presence".equals(matcher.group(2)) ? TopicKind.PRESENCE : TopicKind.MESSAGES;
        return Optional.of(new Topic(communityId, kind));
    }

    public static boolean userQueue(String destination) {
        return USER_ERRORS.equals(destination)
                || USER_PRESENCE.equals(destination)
                || USER_MEMBERSHIP.equals(destination);
    }

    private static Optional<Long> matchId(Pattern pattern, String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
