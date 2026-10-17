package com.developer.copilot.community.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import com.developer.copilot.community.exception.CommunityValidationException;

/**
 * Opaque keyset cursor for {@code (createdAt, messageId)}. The client sends it back unchanged.
 */
public final class CommunityMessageCursor {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String PREFIX = "v1.";

    private CommunityMessageCursor() {
    }

    public record Position(LocalDateTime createdAt, long messageId) {
    }

    public static String encode(LocalDateTime createdAt, long messageId) {
        String raw = createdAt.format(FORMAT) + "|" + messageId;
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static Position decode(String cursor) {
        if (cursor == null || !cursor.startsWith(PREFIX) || cursor.length() == PREFIX.length()) {
            throw new CommunityValidationException(CommunityMessages.INVALID_CURSOR);
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor.substring(PREFIX.length()));
            String raw = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator <= 0 || separator == raw.length() - 1) {
                throw new CommunityValidationException(CommunityMessages.INVALID_CURSOR);
            }
            LocalDateTime createdAt = LocalDateTime.parse(raw.substring(0, separator), FORMAT);
            long messageId = Long.parseLong(raw.substring(separator + 1));
            if (messageId <= 0) {
                throw new CommunityValidationException(CommunityMessages.INVALID_CURSOR);
            }
            return new Position(createdAt, messageId);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new CommunityValidationException(CommunityMessages.INVALID_CURSOR);
        }
    }
}
