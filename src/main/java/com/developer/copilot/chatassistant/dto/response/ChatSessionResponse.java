package com.developer.copilot.chatassistant.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The full chat for one job. If no conversation has been started yet, {@code chatSessionId}
 * and {@code chatTitle} are {@code null} and {@code messages} is an empty list - this is a
 * normal, successful (200) response, not an error, so the frontend can render a fresh chat
 * UI without special-casing a 404.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "The full chat history for one job")
public class ChatSessionResponse {

    @Schema(description = "Null if no chat has been started for this job yet")
    private Long chatSessionId;

    @Schema(description = "ID of the job this chat belongs to", example = "42")
    private Long jobId;

    @Schema(description = "Deterministic title, e.g. 'Amazon - SDE 1'. Null if no chat started yet.")
    private String chatTitle;

    @Schema(description = "Turns in chronological order for the requested page. Empty if no chat started yet.")
    private List<ChatMessageResponse> messages;

    @Schema(description = "Zero-based index of the returned page", example = "0")
    private Integer page;

    @Schema(description = "Maximum number of turns per page", example = "50")
    private Integer size;

    @Schema(description = "Total number of turns across all pages", example = "3")
    private Long totalElements;

    @Schema(description = "Total number of pages available", example = "1")
    private Integer totalPages;
}
