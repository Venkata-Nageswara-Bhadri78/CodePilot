package com.developer.copilot.chatassistant.mapper;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.developer.copilot.chatassistant.dto.response.ChatMessageResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionSummaryResponse;
import com.developer.copilot.chatassistant.entity.ChatMessage;
import com.developer.copilot.chatassistant.entity.ChatSession;
import com.developer.copilot.jobs.entity.JobEntity;

@Component
public class ChatAssistantMapper {

    public ChatMessageResponse toMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .turnNumber(message.getTurnNumber())
                .userPrompt(message.getUserPrompt())
                .aiResponse(message.getAiResponse())
                .createdAt(message.getCreatedAt())
                .build();
    }

    public List<ChatMessageResponse> toMessageResponseList(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    /**
     * @param session {@code null} when no chat has been started for the job yet - the
     *                resulting response's {@code chatSessionId}/{@code chatTitle} will be
     *                {@code null} and {@code messages} empty in that case.
     */
    public ChatSessionResponse toSessionResponse(Long jobId, ChatSession session, List<ChatMessage> messages) {
        return ChatSessionResponse.builder()
                .chatSessionId(session != null ? session.getId() : null)
                .jobId(jobId)
                .chatTitle(session != null ? session.getChatTitle() : null)
                .messages(toMessageResponseList(messages))
                .build();
    }

    public ChatSessionSummaryResponse toSummaryResponse(ChatSession session) {
        JobEntity job = session.getJob();
        return ChatSessionSummaryResponse.builder()
                .chatSessionId(session.getId())
                .jobId(job != null ? job.getId() : null)
                .jobTitle(job != null ? job.getTitle() : null)
                .company(job != null ? job.getCompany() : null)
                .chatTitle(session.getChatTitle())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    public List<ChatSessionSummaryResponse> toSummaryResponseList(List<ChatSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptyList();
        }
        return sessions.stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }
}
