package com.developer.copilot.chatassistant.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight summary of one chat session, used for a chat-list sidebar - deliberately
 * excludes the full message history since that would be wasteful to load for every session
 * just to render a list.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Summary of one job chat, for listing all of a user's chats")
public class ChatSessionSummaryResponse {

    @Schema(description = "Session ID of the chat", example = "15")
    private Long chatSessionId;

    @Schema(description = "ID of the job this chat is about", example = "42")
    private Long jobId;

    @Schema(description = "Title of the associated job", example = "SDE 1")
    private String jobTitle;

    @Schema(description = "Company of the associated job", example = "Amazon")
    private String company;

    @Schema(description = "Auto-generated title, e.g. 'Amazon - SDE 1'", example = "Amazon - SDE 1")
    private String chatTitle;

    @Schema(description = "When this chat was last updated", example = "2026-08-24T15:30:00")
    private LocalDateTime updatedAt;
}
