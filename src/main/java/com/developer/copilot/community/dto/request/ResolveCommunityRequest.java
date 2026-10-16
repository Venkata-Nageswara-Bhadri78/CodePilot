package com.developer.copilot.community.dto.request;

import java.util.List;

import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.service.CommunityLimits;
import com.developer.copilot.community.service.CommunityMessages;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Posting URLs to check. They are not joined and no community is created.")
public class ResolveCommunityRequest {

    @Schema(description = "One or more posting URLs. Tracking parameters are ignored. Job-identifying query parameters are kept.",
            example = "[\"https://jobs.example.com/openings/backend-engineer?utm_source=share\"]")
    @NotNull(message = "At least one posting URL is required.")
    @Size(min = 1, max = CommunityLimits.MAX_URL_ALIASES, message = "Provide between 1 and 20 posting URLs.")
    private List<@NotBlank(message = "Posting URL cannot be blank.") @Size(max = CommunityLimits.MAX_URL_LENGTH, message = "Posting URL cannot exceed 2000 characters.") String> urls;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object ignored) {
        throw new CommunityValidationException(CommunityMessages.UNKNOWN_FIELD);
    }
}
