package com.developer.copilot.community.websocket.dto;

import com.developer.copilot.community.exception.CommunityValidationException;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunitySendPayload {

    public static final String FIELDS = "Send only the message text. A user id is not accepted.";

    private String content;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object ignored) {
        throw new CommunityValidationException(FIELDS);
    }
}
