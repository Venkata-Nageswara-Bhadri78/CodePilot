package com.developer.copilot.chatassistant.service;

import org.springframework.data.domain.Pageable;

import com.developer.copilot.chatassistant.dto.request.SendChatMessageRequest;
import com.developer.copilot.chatassistant.dto.response.ChatSessionListResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;

public interface ChatAssistantService {

    /**
     * Sends a new prompt as part of the chat for the given job, creating the chat session on
     * the very first call for that job. Persists exactly one new {@code ChatMessage} row per
     * call - no rewriting of prior turns. The Gemini call runs outside a database transaction.
     *
     * @param jobId  the job this conversation is about; must be owned by the current user
     * @param request the new prompt
     * @return the session identity plus the newly created turn
     */
    SendChatMessageResponse sendMessage(Long jobId, SendChatMessageRequest request);

    /**
     * Returns a page of the chat (title + ordered turns) for the given job, or an empty result
     * (not a 404) if no conversation has been started yet.
     *
     * @param pageable requested page/size of turns, ordered oldest-first
     */
    ChatSessionResponse getChatHistory(Long jobId, Pageable pageable);

    /**
     * Lists chat sessions owned by the current user, most-recently-updated first.
     */
    ChatSessionListResponse listMyChats(Pageable pageable);

    /**
     * Deletes the entire chat history for a job so the user can start a fresh conversation.
     * Idempotent - if no chat exists for the job, this is a successful no-op rather than an
     * error, matching standard REST DELETE semantics.
     */
    void deleteChat(Long jobId);
}
