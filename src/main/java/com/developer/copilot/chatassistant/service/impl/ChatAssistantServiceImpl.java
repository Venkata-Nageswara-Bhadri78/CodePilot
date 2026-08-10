package com.developer.copilot.chatassistant.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.ai.dto.request.ChatTurnDto;
import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.chatassistant.dto.request.SendChatMessageRequest;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionSummaryResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;
import com.developer.copilot.chatassistant.entity.ChatMessage;
import com.developer.copilot.chatassistant.entity.ChatSession;
import com.developer.copilot.chatassistant.exception.ChatSessionNotFoundException;
import com.developer.copilot.chatassistant.mapper.ChatAssistantMapper;
import com.developer.copilot.chatassistant.repository.ChatMessageRepository;
import com.developer.copilot.chatassistant.repository.ChatSessionRepository;
import com.developer.copilot.chatassistant.service.ChatAssistantService;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns all persistence for job-scoped chat history. AI prompt construction and model calls
 * stay entirely inside {@link AiService} - this class only ever passes plain data
 * ({@link JobChatAiRequest}) across that boundary, per the module's separation-of-concerns
 * constraint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAssistantServiceImpl implements ChatAssistantService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final ChatAssistantMapper chatAssistantMapper;

    @Override
    @Transactional
    public SendChatMessageResponse sendMessage(Long jobId, SendChatMessageRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobForCurrentUser(jobId, currentUser);

        ChatSession session = chatSessionRepository.findByJobId(jobId)
                .orElseGet(() -> createSession(job, currentUser));

        List<ChatMessage> priorMessages = chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(session.getId());
        List<ChatTurnDto> priorTurns = priorMessages.stream()
                .map(message -> ChatTurnDto.builder()
                        .userPrompt(message.getUserPrompt())
                        .aiResponse(message.getAiResponse())
                        .build())
                .collect(Collectors.toList());

        JobChatAiRequest aiRequest = JobChatAiRequest.builder()
                .jobId(jobId)
                .priorTurns(priorTurns)
                .newPrompt(request.getPrompt())
                .build();

        AiChatResponse aiResponse = aiService.continueJobChat(aiRequest, currentUser.getEmail());

        ChatMessage newMessage = ChatMessage.builder()
                .chatSession(session)
                .turnNumber(priorMessages.size() + 1)
                .userPrompt(request.getPrompt())
                .aiResponse(aiResponse.getContent())
                .build();
        chatMessageRepository.save(newMessage);

        return SendChatMessageResponse.builder()
                .chatSessionId(session.getId())
                .chatTitle(session.getChatTitle())
                .latestTurn(chatAssistantMapper.toMessageResponse(newMessage))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionResponse getChatHistory(Long jobId) {
        User currentUser = getCurrentUser();
        getJobForCurrentUser(jobId, currentUser);

        Optional<ChatSession> sessionOptional = chatSessionRepository.findByJobId(jobId);
        if (sessionOptional.isEmpty()) {
            return chatAssistantMapper.toSessionResponse(jobId, null, Collections.emptyList());
        }

        ChatSession session = sessionOptional.get();
        List<ChatMessage> messages = chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(session.getId());
        return chatAssistantMapper.toSessionResponse(jobId, session, messages);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionSummaryResponse> listMyChats() {
        User currentUser = getCurrentUser();
        List<ChatSession> sessions = chatSessionRepository.findAllByUserIdWithJob(currentUser.getId());
        return chatAssistantMapper.toSummaryResponseList(sessions);
    }

    @Override
    @Transactional
    public void deleteChat(Long jobId) {
        User currentUser = getCurrentUser();
        getJobForCurrentUser(jobId, currentUser);

        ChatSession session = chatSessionRepository.findByJobId(jobId)
                .orElseThrow(() -> new ChatSessionNotFoundException("No chat found for job id: " + jobId));

        chatMessageRepository.deleteByChatSessionId(session.getId());
        chatSessionRepository.delete(session);
    }

    private ChatSession createSession(JobEntity job, User currentUser) {
        ChatSession session = ChatSession.builder()
                .job(job)
                .user(currentUser)
                .chatTitle(buildChatTitle(job))
                .build();
        return chatSessionRepository.save(session);
    }

    /**
     * Deterministic chat title - "{Company} - {Title}" - no AI call needed just to name a chat.
     */
    private String buildChatTitle(JobEntity job) {
        String company = job.getCompany() != null ? job.getCompany().trim() : "";
        String title = job.getTitle() != null ? job.getTitle().trim() : "";
        if (!company.isEmpty() && !title.isEmpty()) {
            return company + " - " + title;
        }
        return !company.isEmpty() ? company : title;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidCredentialsException("User is not authenticated.");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User account not found."));
    }

    private JobEntity getJobForCurrentUser(Long jobId, User currentUser) {
        return jobRepository.findByIdAndUserId(jobId, currentUser.getId())
                .orElseThrow(() -> new JobNotFoundException("Job not found with id: " + jobId));
    }
}
