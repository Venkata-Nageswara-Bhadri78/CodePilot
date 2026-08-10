package com.developer.copilot.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The AI-facing shape of one previously-persisted conversational turn (a user prompt paired
 * with the AI's response to it), used to reconstruct multi-turn context for
 * {@link com.developer.copilot.ai.service.AiService#continueJobChat}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One historical prompt/response pair from a prior chat turn")
public class ChatTurnDto {

    @Schema(description = "The user's prompt from this historical turn")
    private String userPrompt;

    @Schema(description = "The AI's response from this historical turn")
    private String aiResponse;
}
