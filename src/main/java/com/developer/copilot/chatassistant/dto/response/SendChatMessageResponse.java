package com.developer.copilot.chatassistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Result of sending a chat message: the session identity (useful so the frontend learns the
 * session/title on the very first message, when a session is created lazily) plus only the
 * newly-created turn - never the whole history, so repeated sends stay cheap regardless of
 * how long the conversation has grown.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Result of sending a new chat message")
public class SendChatMessageResponse {

    private Long chatSessionId;

    private String chatTitle;

    private ChatMessageResponse latestTurn;
}
