package com.developer.copilot.chatassistant.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.ai.dto.request.ChatTurnDto;
import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.chatassistant.dto.request.SendChatMessageRequest;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionSummaryResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;
import com.developer.copilot.chatassistant.entity.ChatMessage;
import com.developer.copilot.chatassistant.entity.ChatSession;
import com.developer.copilot.chatassistant.mapper.ChatAssistantMapper;
import com.developer.copilot.chatassistant.repository.ChatMessageRepository;
import com.developer.copilot.chatassistant.repository.ChatSessionRepository;
import com.developer.copilot.chatassistant.service.ChatAssistantService;
import com.developer.copilot.common.security.CurrentUserService;
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
    private final AiService aiService;
    private final ChatAssistantMapper chatAssistantMapper;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public SendChatMessageResponse sendMessage(Long jobId, SendChatMessageRequest request) {
        User currentUser = getCurrentUser();
        JobEntity job = getJobForCurrentUser(jobId, currentUser);

        Optional<ChatSession> existingSession = chatSessionRepository.findByJobId(jobId);
        ChatSession session = existingSession.orElseGet(() -> createSession(job, currentUser));
        if (existingSession.isEmpty()) {
            log.info("Created new chat session: chatSessionId={} jobId={} userId={}",
                    session.getId(), jobId, currentUser.getId());
        }

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

        AiChatResponse aiResponse;
        try {
            aiResponse = aiService.continueJobChat(aiRequest, currentUser.getEmail());
        } catch (RuntimeException e) {
            log.error("AI service failed for jobId={} userId={}: {}", jobId, currentUser.getId(), e.getMessage());
            throw e;
        }

        ChatMessage newMessage = ChatMessage.builder()
                .chatSession(session)
                .turnNumber(priorMessages.size() + 1)
                .userPrompt(request.getPrompt())
                .aiResponse(aiResponse.getContent())
                .build();
        chatMessageRepository.save(newMessage);

        log.info("Message sent: chatSessionId={} turnNumber={} userId={}",
                session.getId(), newMessage.getTurnNumber(), currentUser.getId());

        return SendChatMessageResponse.builder()
                .chatSessionId(session.getId())
                .chatTitle(session.getChatTitle())
                .latestTurn(chatAssistantMapper.toMessageResponse(newMessage))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionResponse getChatHistory(Long jobId, Pageable pageable) {
        User currentUser = getCurrentUser();
        getJobForCurrentUser(jobId, currentUser);

        Optional<ChatSession> sessionOptional = chatSessionRepository.findByJobId(jobId);
        if (sessionOptional.isEmpty()) {
            return chatAssistantMapper.toSessionResponse(jobId, null, Page.empty(pageable));
        }

        ChatSession session = sessionOptional.get();
        Page<ChatMessage> messages = chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(session.getId(), pageable);
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

        Optional<ChatSession> sessionOptional = chatSessionRepository.findByJobId(jobId);
        if (sessionOptional.isEmpty()) {
            log.info("Delete requested for job with no chat session (idempotent no-op): jobId={} userId={}",
                    jobId, currentUser.getId());
            return;
        }

        ChatSession session = sessionOptional.get();
        chatMessageRepository.deleteByChatSessionId(session.getId());
        chatSessionRepository.delete(session);

        log.info("Chat deleted: chatSessionId={} jobId={} userId={}", session.getId(), jobId, currentUser.getId());
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
     * Computed once, at session creation, and intentionally never recomputed afterwards: the
     * chat title is a snapshot of the job at the time the conversation started, not a live
     * mirror of the job's current title/company.
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
        return currentUserService.getCurrentUser();
    }

    private JobEntity getJobForCurrentUser(Long jobId, User currentUser) {
        return jobRepository.findByIdAndUserId(jobId, currentUser.getId())
                .orElseThrow(() -> new JobNotFoundException("Job not found with id: " + jobId));
    }
}
