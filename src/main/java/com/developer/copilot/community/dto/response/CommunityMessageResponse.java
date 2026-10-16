package com.developer.copilot.community.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "One stored text message. The id and createdAt are assigned by the server.")
public class CommunityMessageResponse {

    @Schema(description = "Stable server id. Use it to ignore a live copy of a message already shown from history.", example = "42")
    private Long id;

    @Schema(example = "10")
    private Long communityId;

    private CommunityMessageSenderResponse sender;

    @Schema(example = "Has anyone heard back?")
    private String content;

    @Schema(description = "When the server stored the message.", example = "2026-10-16T13:00:00")
    private LocalDateTime createdAt;
}
