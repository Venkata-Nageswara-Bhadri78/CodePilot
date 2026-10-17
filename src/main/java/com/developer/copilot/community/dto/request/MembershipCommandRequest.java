package com.developer.copilot.community.dto.request;

import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.service.CommunityMessages;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Join and leave take no fields. The member is the signed-in user.")
public class MembershipCommandRequest {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object ignored) {
        throw new CommunityValidationException(CommunityMessages.NO_FIELDS);
    }
}
