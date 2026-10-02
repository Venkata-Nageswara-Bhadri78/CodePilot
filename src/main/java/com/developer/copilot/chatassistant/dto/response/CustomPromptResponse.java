package com.developer.copilot.chatassistant.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One saved custom prompt owned by the current user. userId is not exposed.")
public class CustomPromptResponse {

    @Schema(description = "Unique id of this custom prompt", example = "7")
    private Long id;

    @Schema(description = "Short label for the sidebar list", example = "Resume rewrite for this job")
    private String title;

    @Schema(description = "The reusable prompt text")
    private String prompt;

    @Schema(description = "When the row was created (server LocalDateTime, no timezone offset)",
            example = "2026-09-30T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "When the row was last updated (server LocalDateTime, no timezone offset)",
            example = "2026-09-30T12:30:00")
    private LocalDateTime updatedAt;
}
