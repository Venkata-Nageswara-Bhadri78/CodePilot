package com.developer.copilot.chatassistant.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.developer.copilot.ai.dto.request.ChatTurnDto;
import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.chatassistant.dto.request.SendChatMessageRequest;
import com.developer.copilot.chatassistant.dto.response.ChatSessionListResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;
import com.developer.copilot.chatassistant.entity.ChatMessage;
import com.developer.copilot.chatassistant.entity.ChatSession;
import com.developer.copilot.chatassistant.exception.ChatConflictException;
import com.developer.copilot.chatassistant.mapper.ChatAssistantMapper;
import com.developer.copilot.chatassistant.metrics.ChatAssistantMetrics;
import com.developer.copilot.chatassistant.repository.ChatMessageRepository;
import com.developer.copilot.chatassistant.repository.ChatSessionRepository;
import com.developer.copilot.chatassistant.service.ChatAssistantService;
import com.developer.copilot.chatassistant.service.ChatAssistantTransactionRunner;
import com.developer.copilot.chatassistant.util.ChatAssistantHtmlSanitizer;
import com.developer.copilot.chatassistant.util.ChatAssistantLimits;
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

    private static final String CONCURRENT_SEND_MESSAGE =
            "This chat was updated at the same time. Please retry.";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final JobRepository jobRepository;
    private final AiService aiService;
    private final ChatAssistantMapper chatAssistantMapper;
    private final CurrentUserService currentUserService;
    private final ChatAssistantTransactionRunner transactionRunner;
    private final ChatAssistantMetrics metrics;

    private final ConcurrentHashMap<Long, ReentrantLock> sendLocks = new ConcurrentHashMap<>();

    @Override
    public SendChatMessageResponse sendMessage(Long jobId, SendChatMessageRequest request) {
        User currentUser = getCurrentUser();
        ReentrantLock lock = sendLocks.computeIfAbsent(jobId, id -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new ChatConflictException(CONCURRENT_SEND_MESSAGE);
        }
        try {
            return sendLocked(jobId, request, currentUser);
        } finally {
            lock.unlock();
        }
    }

    private SendChatMessageResponse sendLocked(Long jobId, SendChatMessageRequest request, User currentUser) {
        PreparedSend prepared = transactionRunner.execute(() -> prepareSend(jobId, currentUser, request.getPrompt()));

        Instant aiStarted = Instant.now();
        AiChatResponse aiResponse;
        try {
            aiResponse = aiService.continueJobChat(prepared.aiRequest(), currentUser.getEmail());
        } catch (RuntimeException ex) {
            metrics.recordProviderFailure();
            log.error("AI service failed for jobId={} userId={}: {}", jobId, currentUser.getId(), ex.getMessage());
            discardEmptySessionIfCreated(prepared);
            throw ex;
        }
        Duration aiLatency = Duration.between(aiStarted, Instant.now());

        String content = aiResponse == null ? "" : ChatAssistantHtmlSanitizer.stripScripts(aiResponse.getContent());
        if (!StringUtils.hasText(content)) {
            metrics.recordBlankResponse();
            discardEmptySessionIfCreated(prepared);
            throw new AiServiceException("The AI service returned an empty response. Please try again.");
        }

        SendChatMessageResponse response = transactionRunner.execute(
                () -> persistTurn(jobId, currentUser, prepared, request.getPrompt(), content));
        metrics.recordSendSuccess(aiLatency);
        return response;
    }

    private PreparedSend prepareSend(Long jobId, User currentUser, String newPrompt) {
        JobEntity job = getJobForCurrentUser(jobId, currentUser);

        boolean createdSession = false;
        ChatSession session = chatSessionRepository.findByJobIdAndUserId(jobId, currentUser.getId()).orElse(null);
        if (session == null) {
            try {
                session = createSession(job, currentUser);
                createdSession = true;
                log.info("Created new chat session: chatSessionId={} jobId={} userId={}",
                        session.getId(), jobId, currentUser.getId());
            } catch (DataIntegrityViolationException ex) {
                session = chatSessionRepository.findByJobIdAndUserId(jobId, currentUser.getId())
                        .orElseThrow(() -> new ChatConflictException(CONCURRENT_SEND_MESSAGE));
            }
        }

        List<ChatTurnDto> priorTurns = loadPriorTurnsForModel(session.getId());
        JobChatAiRequest aiRequest = JobChatAiRequest.builder()
                .jobId(jobId)
                .priorTurns(priorTurns)
                .newPrompt(newPrompt)
                .build();

        return new PreparedSend(session.getId(), session.getChatTitle(), createdSession, aiRequest);
    }

    private List<ChatTurnDto> loadPriorTurnsForModel(Long sessionId) {
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findRecentByChatSessionId(
                sessionId, PageRequest.of(0, ChatAssistantLimits.MAX_PRIOR_TURNS)));
        Collections.reverse(recent);
        return recent.stream()
                .filter(message -> StringUtils.hasText(message.getUserPrompt())
                        && StringUtils.hasText(message.getAiResponse()))
                .map(message -> ChatTurnDto.builder()
                        .userPrompt(message.getUserPrompt())
                        .aiResponse(message.getAiResponse())
                        .build())
                .collect(Collectors.toList());
    }

    private SendChatMessageResponse persistTurn(
            Long jobId,
            User currentUser,
            PreparedSend prepared,
            String prompt,
            String aiContent) {
        JobEntity job = getJobForCurrentUser(jobId, currentUser);
        ChatSession session = chatSessionRepository.findByIdForUpdate(prepared.sessionId())
                .or(() -> chatSessionRepository.findByJobIdAndUserId(jobId, currentUser.getId()))
                .orElseGet(() -> createSession(job, currentUser));

        try {
            return insertTurn(session, prompt, aiContent);
        } catch (DataIntegrityViolationException ex) {
            metrics.recordConflict();
            ChatSession locked = chatSessionRepository.findByIdForUpdate(session.getId())
                    .orElse(session);
            try {
                return insertTurn(locked, prompt, aiContent);
            } catch (DataIntegrityViolationException retry) {
                metrics.recordConflict();
                throw new ChatConflictException(CONCURRENT_SEND_MESSAGE);
            }
        }
    }

    private SendChatMessageResponse insertTurn(ChatSession session, String prompt, String aiContent) {
        int nextTurn = chatMessageRepository.findMaxTurnNumber(session.getId()) + 1;
        ChatMessage newMessage = ChatMessage.builder()
                .chatSession(session)
                .turnNumber(nextTurn)
                .userPrompt(prompt)
                .aiResponse(aiContent)
                .build();
        chatMessageRepository.save(newMessage);

        session.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.save(session);

        log.info("Message sent: chatSessionId={} turnNumber={} userId={}",
                session.getId(), newMessage.getTurnNumber(), session.getUser() != null ? session.getUser().getId() : null);

        return SendChatMessageResponse.builder()
                .chatSessionId(session.getId())
                .chatTitle(session.getChatTitle())
                .latestTurn(chatAssistantMapper.toMessageResponse(newMessage))
                .build();
    }

    private void discardEmptySessionIfCreated(PreparedSend prepared) {
        if (!prepared.createdSession()) {
            return;
        }
        transactionRunner.execute(() -> {
            if (chatMessageRepository.findMaxTurnNumber(prepared.sessionId()) == 0) {
                chatMessageRepository.deleteByChatSessionId(prepared.sessionId());
                chatSessionRepository.findById(prepared.sessionId()).ifPresent(chatSessionRepository::delete);
                log.info("Discarded empty chat session after AI failure: chatSessionId={}", prepared.sessionId());
            }
            return null;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionResponse getChatHistory(Long jobId, Pageable pageable) {
        User currentUser = getCurrentUser();
        getJobForCurrentUser(jobId, currentUser);

        Optional<ChatSession> sessionOptional = chatSessionRepository.findByJobIdAndUserId(jobId, currentUser.getId());
        if (sessionOptional.isEmpty()) {
            return chatAssistantMapper.toSessionResponse(jobId, null, Page.empty(pageable));
        }

        ChatSession session = sessionOptional.get();
        Page<ChatMessage> messages = chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(
                session.getId(), pageable);
        return chatAssistantMapper.toSessionResponse(jobId, session, messages);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionListResponse listMyChats(Pageable pageable) {
        User currentUser = getCurrentUser();
        Page<ChatSession> sessions = chatSessionRepository.findAllByUserIdOrderByUpdatedAtDesc(
                currentUser.getId(), pageable);
        return chatAssistantMapper.toListResponse(sessions);
    }

    @Override
    @Transactional
    public void deleteChat(Long jobId) {
        User currentUser = getCurrentUser();
        getJobForCurrentUser(jobId, currentUser);

        Optional<ChatSession> sessionOptional = chatSessionRepository.findByJobIdAndUserId(jobId, currentUser.getId());
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
     * mirror of the job's current title/company. Clamped to 255 so legal job fields cannot fail insert.
     */
    private String buildChatTitle(JobEntity job) {
        String company = job.getCompany() != null ? job.getCompany().trim() : "";
        String title = job.getTitle() != null ? job.getTitle().trim() : "";
        String built;
        if (!company.isEmpty() && !title.isEmpty()) {
            built = company + " - " + title;
        } else {
            built = !company.isEmpty() ? company : title;
        }
        return clampTitle(built);
    }

    private static String clampTitle(String title) {
        if (title.length() <= ChatAssistantLimits.MAX_CHAT_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, ChatAssistantLimits.MAX_CHAT_TITLE_LENGTH - 3) + "...";
    }

    private User getCurrentUser() {
        return currentUserService.getCurrentUser();
    }

    private JobEntity getJobForCurrentUser(Long jobId, User currentUser) {
        return jobRepository.findByIdAndUserId(jobId, currentUser.getId())
                .orElseThrow(() -> {
                    metrics.recordJobNotFound();
                    return new JobNotFoundException(ChatAssistantLimits.JOB_NOT_FOUND);
                });
    }

    private record PreparedSend(
            Long sessionId,
            String chatTitle,
            boolean createdSession,
            JobChatAiRequest aiRequest) {
    }
}
