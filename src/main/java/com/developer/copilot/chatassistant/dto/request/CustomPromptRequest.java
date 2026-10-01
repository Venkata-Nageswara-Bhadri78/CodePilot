package com.developer.copilot.chatassistant.dto.request;

import com.developer.copilot.chatassistant.util.CustomPromptLimits;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Create or fully replace a saved custom prompt. Both title and prompt are required.")
public class CustomPromptRequest {

    @NotBlank(message = "Title cannot be blank.")
    @Size(max = CustomPromptLimits.MAX_TITLE_LENGTH,
            message = "Title cannot exceed " + CustomPromptLimits.MAX_TITLE_LENGTH + " characters.")
    @Schema(
            description = "Short label shown in the custom-prompts list. Required. Max 255 characters. "
                    + "Not unique — two prompts may share a title.",
            example = "Resume rewrite for this job",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = CustomPromptLimits.MAX_TITLE_LENGTH)
    private String title;

    @NotBlank(message = "Prompt cannot be blank.")
    @Size(max = CustomPromptLimits.MAX_PROMPT_LENGTH,
            message = "Prompt cannot exceed " + CustomPromptLimits.MAX_PROMPT_LENGTH + " characters.")
    @Schema(
            description = "The reusable prompt text. Required. Max 8000 characters (same cap as a chat send).",
            example = "Rewrite my resume so it aligns strongly with this job description.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = CustomPromptLimits.MAX_PROMPT_LENGTH)
    private String prompt;
}
