package com.developer.copilot.community.websocket.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunitySocketError {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String MEMBERSHIP_REQUIRED = "MEMBERSHIP_REQUIRED";
    public static final String ACCOUNT_REQUIRED = "ACCOUNT_REQUIRED";
    public static final String TERMS_REQUIRED = "TERMS_REQUIRED";
    public static final String COMMUNITY_UNAVAILABLE = "COMMUNITY_UNAVAILABLE";
    public static final String INVALID_MESSAGE = "INVALID_MESSAGE";
    public static final String DESTINATION_NOT_ALLOWED = "DESTINATION_NOT_ALLOWED";
    public static final String SEND_FAILED = "SEND_FAILED";

    private String code;
    private String message;
}
