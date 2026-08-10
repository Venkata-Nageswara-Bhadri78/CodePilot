package com.developer.copilot.chatassistant.service;

import java.util.List;

import com.developer.copilot.chatassistant.dto.request.SendChatMessageRequest;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionSummaryResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;

public interface ChatAssistantService {

    /**
     * Sends a new prompt as part of the chat for the given job, creating the chat session on
     * the very first call for that job. Persists exactly one new {@code ChatMessage} row per
     * call - no rewriting of prior turns.
     *
     * @param jobId  the job this conversation is about; must be owned by the current user
     * @param request the new prompt
     * @return the session identity plus the newly created turn
     */
    SendChatMessageResponse sendMessage(Long jobId, SendChatMessageRequest request);

    /**
     * Returns the full chat (title + ordered turns) for the given job, or an empty result
     * (not a 404) if no conversation has been started yet.
     */
    ChatSessionResponse getChatHistory(Long jobId);

    /**
     * Lists all chat sessions owned by the current user, most-recently-updated first.
     */
    List<ChatSessionSummaryResponse> listMyChats();

    /**
     * Deletes the entire chat history for a job so the user can start a fresh conversation.
     *
     * @throws com.developer.copilot.chatassistant.exception.ChatSessionNotFoundException if no chat exists for that job
     */
    void deleteChat(Long jobId);
}
