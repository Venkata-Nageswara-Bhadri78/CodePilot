package com.developer.copilot.community.dto.request;

import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.service.CommunityLimits;
import com.developer.copilot.community.service.CommunityMessages;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefineCommunityDescriptionRequest {

    @Schema(description = "Description to rewrite. Only this text is sent to the model.")
    @NotBlank(message = CommunityMessages.DESCRIPTION_REQUIRED)
    @Size(max = CommunityLimits.MAX_DESCRIPTION_LENGTH, message = "Description cannot exceed 50000 characters.")
    private String description;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object ignored) {
        throw new CommunityValidationException(CommunityMessages.UNKNOWN_FIELD);
    }
}
