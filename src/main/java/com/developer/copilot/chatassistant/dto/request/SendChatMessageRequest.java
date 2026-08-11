package com.developer.copilot.chatassistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A new prompt sent as part of an ongoing (or brand new) job-scoped chat.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A new chat message to send about a specific job")
public class SendChatMessageRequest {

    @NotBlank(message = "Prompt cannot be blank.")
    @Size(max = 8000, message = "Prompt cannot exceed 8000 characters.")
    @Schema(description = "The user's message/question", example = "How well do I match the required experience for this role?")
    private String prompt;
}
