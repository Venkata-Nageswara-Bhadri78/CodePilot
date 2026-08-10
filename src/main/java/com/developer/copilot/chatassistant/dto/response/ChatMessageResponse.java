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

    private Long id;

    private Integer turnNumber;

    private String userPrompt;

    private String aiResponse;

    private LocalDateTime createdAt;
}
