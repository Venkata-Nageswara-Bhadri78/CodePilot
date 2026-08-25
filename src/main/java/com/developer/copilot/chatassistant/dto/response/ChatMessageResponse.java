package com.developer.copilot.chatassistant.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single persisted conversational turn (one user prompt + its AI response).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "One turn of a job chat conversation")
public class ChatMessageResponse {

    @Schema(description = "Unique message ID", example = "42")
    private Long id;

    @Schema(description = "1-based turn position in this conversation", example = "3")
    private Integer turnNumber;

    @Schema(description = "What the user asked", example = "How well do I match this role?")
    private String userPrompt;

    @Schema(description = "What the AI replied", example = "You match 4 out of 6 required skills...")
    private String aiResponse;

    @Schema(description = "When this turn was created", example = "2026-08-24T15:30:00")
    private LocalDateTime createdAt;
}
