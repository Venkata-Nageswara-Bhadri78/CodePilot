package com.developer.copilot.ai.dto.response;

import java.time.LocalDateTime;

import com.developer.copilot.ai.dto.request.AiMode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload for synchronous AI completion")
public class AiChatResponse {

    @Schema(description = "Full generated AI response in structured Markdown format",
            example = "### Resume Match Analysis\n\n**Overall Match Score:** 88%\n\n**Strengths:**\n- Strong Spring Boot and Java experience...")
    private String content;

    @Schema(description = "Underlying LLM model name used for generation",
            example = "gemini-flash-latest")
    private String model;

    @Schema(description = "Generation finish reason",
            example = "STOP")
    private String finishReason;

    @Schema(description = "The situational mode of the interaction")
    private AiMode mode;

    @Schema(description = "Prompt tokens consumed", example = "420")
    private Long promptTokens;

    @Schema(description = "Completion tokens generated", example = "280")
    private Long completionTokens;

    @Schema(description = "Total tokens consumed", example = "700")
    private Long totalTokens;

    @Schema(description = "Response generation timestamp")
    private LocalDateTime timestamp;
}
