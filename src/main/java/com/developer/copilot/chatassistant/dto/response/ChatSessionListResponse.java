package com.developer.copilot.chatassistant.dto.response;

import java.util.List;

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
@Schema(description = "Paged sidebar of the current user's job chats, newest-updated first")
public class ChatSessionListResponse {

    @Schema(description = "Chat summaries for this page")
    private List<ChatSessionSummaryResponse> chats;

    @Schema(description = "Zero-based page index", example = "0")
    private Integer page;

    @Schema(description = "Page size (max 50)", example = "50")
    private Integer size;

    @Schema(description = "Total number of chat sessions", example = "3")
    private Long totalElements;

    @Schema(description = "Total number of pages", example = "1")
    private Integer totalPages;
}
