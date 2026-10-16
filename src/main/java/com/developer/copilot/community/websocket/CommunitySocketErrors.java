package com.developer.copilot.community.websocket;

import com.developer.copilot.community.exception.CommunityAccountRequiredException;
import com.developer.copilot.community.exception.CommunityMembershipRequiredException;
import com.developer.copilot.community.exception.CommunityUnavailableException;
import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.exception.TermsAcceptanceRequiredException;
import com.developer.copilot.community.service.CommunityMessages;
import com.developer.copilot.community.websocket.dto.CommunitySocketError;

public final class CommunitySocketErrors {

    public static final String DESTINATION = "That destination is not available.";
    public static final String SEND_FAILED = "The message could not be sent.";

    private CommunitySocketErrors() {
    }

    public static CommunitySocketError from(Throwable failure) {
        Throwable cause = communityCause(failure);
        if (cause instanceof CommunityMembershipRequiredException ex) {
            return error(CommunitySocketError.MEMBERSHIP_REQUIRED, ex.getMessage());
        }
        if (cause instanceof CommunityAccountRequiredException ex) {
            return error(CommunitySocketError.ACCOUNT_REQUIRED, ex.getMessage());
        }
        if (cause instanceof TermsAcceptanceRequiredException ex) {
            return error(CommunitySocketError.TERMS_REQUIRED, ex.getMessage());
        }
        if (cause instanceof CommunityUnavailableException ex) {
            return error(CommunitySocketError.COMMUNITY_UNAVAILABLE, ex.getMessage());
        }
        if (cause instanceof CommunityValidationException ex) {
            return error(CommunitySocketError.INVALID_MESSAGE, safeValidation(ex.getMessage()));
        }
        return error(CommunitySocketError.SEND_FAILED, SEND_FAILED);
    }

    public static CommunitySocketError destinationRejected() {
        return error(CommunitySocketError.DESTINATION_NOT_ALLOWED, DESTINATION);
    }

    private static String safeValidation(String message) {
        if (CommunityMessages.MESSAGE_REQUIRED.equals(message)
                || CommunityMessages.MESSAGE_INVALID.equals(message)
                || CommunityMessages.INVALID_COMMUNITY_ID.equals(message)
                || (message != null && message.startsWith("Message cannot exceed "))) {
            return message;
        }
        if (com.developer.copilot.community.websocket.dto.CommunitySendPayload.FIELDS.equals(message)) {
            return message;
        }
        return CommunityMessages.MESSAGE_REQUIRED;
    }

    private static Throwable communityCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CommunityMembershipRequiredException
                    || current instanceof CommunityAccountRequiredException
                    || current instanceof TermsAcceptanceRequiredException
                    || current instanceof CommunityUnavailableException
                    || current instanceof CommunityValidationException) {
                return current;
            }
            current = current.getCause();
        }
        return failure;
    }

    private static CommunitySocketError error(String code, String message) {
        return CommunitySocketError.builder().code(code).message(message).build();
    }
}
