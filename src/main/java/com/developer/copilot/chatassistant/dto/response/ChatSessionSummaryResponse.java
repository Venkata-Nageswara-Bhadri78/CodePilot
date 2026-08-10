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

    private Long chatSessionId;

    private Long jobId;

    private String jobTitle;

    private String company;

    private String chatTitle;

    private LocalDateTime updatedAt;
}
