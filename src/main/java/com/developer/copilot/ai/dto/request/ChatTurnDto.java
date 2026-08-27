package com.developer.copilot.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One previously-persisted conversational turn used to reconstruct multi-turn context for
 * {@link com.developer.copilot.ai.service.AiService#continueJobChat}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One historical prompt/response pair from a prior chat turn (internal contract)")
public class ChatTurnDto {

    @NotBlank(message = "Prior user prompt cannot be blank.")
    @Size(max = 8000, message = "Prior user prompt cannot exceed 8000 characters.")
    @Schema(description = "The user's prompt from this historical turn", maxLength = 8000)
    private String userPrompt;

    @NotBlank(message = "Prior AI response cannot be blank.")
    @Size(max = 16000, message = "Prior AI response cannot exceed 16000 characters.")
    @Schema(description = "The AI's response from this historical turn", maxLength = 16000)
    private String aiResponse;
}
