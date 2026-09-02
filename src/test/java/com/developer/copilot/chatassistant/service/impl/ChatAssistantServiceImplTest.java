package com.developer.copilot.chatassistant.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
import com.developer.copilot.chatassistant.service.ChatAssistantTransactionRunner;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.repository.JobRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAssistantServiceImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private AiService aiService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ChatAssistantTransactionRunner transactionRunner;

    @Spy
    private ChatAssistantMapper chatAssistantMapper = new ChatAssistantMapper();

    @Spy
    private ChatAssistantMetrics metrics = new ChatAssistantMetrics();

    @InjectMocks
    private ChatAssistantServiceImpl chatAssistantService;

    @Captor
    private ArgumentCaptor<ChatMessage> chatMessageCaptor;

    @Captor
    private ArgumentCaptor<JobChatAiRequest> aiRequestCaptor;

    private User testUser;
    private JobEntity testJob;

    private static final String USER_EMAIL = "candidate@example.com";
    private static final Long USER_ID = 1L;
    private static final Long JOB_ID = 100L;
    private static final Long SESSION_ID = 500L;

    @BeforeEach
    void stubTransactions() {
        lenient().when(transactionRunner.execute(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    private void setUpAuthenticatedUser() {
        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setEmail(USER_EMAIL);

        testJob = JobEntity.builder()
                .id(JOB_ID)
                .user(testUser)
                .title("SDE 1")
                .company("Amazon")
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(testUser);
    }

    private void stubNewSessionPersist() {
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(SESSION_ID);
            }
            return saved;
        });
        when(chatMessageRepository.findRecentByChatSessionId(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of());
        ChatSession locked = ChatSession.builder()
                .id(SESSION_ID)
                .job(testJob)
                .user(testUser)
                .chatTitle("Amazon - SDE 1")
                .build();
        lenient().when(chatSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(locked));
        lenient().when(chatMessageRepository.findMaxTurnNumber(SESSION_ID)).thenReturn(0);
        lenient().when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendMessage_firstMessage_createsSessionWithDeterministicTitle() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        stubNewSessionPersist();
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("You're a strong match for this role.").build());

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("How well do I match this role?").build());

        assertEquals(SESSION_ID, response.getChatSessionId());
        assertEquals("Amazon - SDE 1", response.getChatTitle());
        assertEquals("You're a strong match for this role.", response.getLatestTurn().getAiResponse());
        assertEquals(1, response.getLatestTurn().getTurnNumber());

        verify(aiService).continueJobChat(aiRequestCaptor.capture(), eq(USER_EMAIL));
        assertTrue(aiRequestCaptor.getValue().getPriorTurns().isEmpty());
        assertNull(aiRequestCaptor.getValue().getResumeId());
        assertNull(aiRequestCaptor.getValue().getCustomResumeText());
        verify(chatMessageRepository).save(chatMessageCaptor.capture());
        assertEquals(1, chatMessageCaptor.getValue().getTurnNumber());
    }

    @Test
    void sendMessage_secondMessage_reusesSessionAndIncrementsTurnNumber() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID)
                .job(testJob)
                .user(testUser)
                .chatTitle("Amazon - SDE 1")
                .build();
        ChatMessage firstTurn = ChatMessage.builder()
                .id(1L)
                .chatSession(existingSession)
                .turnNumber(1)
                .userPrompt("First question")
                .aiResponse("First answer")
                .build();

        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findRecentByChatSessionId(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of(firstTurn));
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Second answer").build());
        when(chatSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findMaxTurnNumber(SESSION_ID)).thenReturn(1);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Second question").build());

        assertEquals(2, response.getLatestTurn().getTurnNumber());
        verify(aiService).continueJobChat(aiRequestCaptor.capture(), eq(USER_EMAIL));
        assertEquals(1, aiRequestCaptor.getValue().getPriorTurns().size());
        assertEquals("First question", aiRequestCaptor.getValue().getPriorTurns().get(0).getUserPrompt());
        verify(chatSessionRepository, times(1)).save(any(ChatSession.class));
        assertNotNull(existingSession.getUpdatedAt());
    }

    @Test
    void sendMessage_doesNotLoadFullHistory() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findRecentByChatSessionId(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Answer").build());
        when(chatSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findMaxTurnNumber(SESSION_ID)).thenReturn(0);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatAssistantService.sendMessage(JOB_ID, SendChatMessageRequest.builder().prompt("Hi").build());

        verify(chatMessageRepository, never()).findAllByChatSessionIdOrderByTurnNumberAsc(anyLong(), any());
    }

    @Test
    void sendMessage_nullAiContent_doesNotPersist() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findRecentByChatSessionId(eq(SESSION_ID), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content(null).build());

        AiServiceException ex = assertThrows(AiServiceException.class, () -> chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hello").build()));
        assertEquals("The AI service returned an empty response. Please try again.", ex.getMessage());
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_inFlightSameJob_secondRequestConflicts() throws Exception {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build();
        CountDownLatch inAi = new CountDownLatch(1);
        CountDownLatch releaseAi = new CountDownLatch(1);

        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findRecentByChatSessionId(eq(SESSION_ID), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL))).thenAnswer(invocation -> {
            inAi.countDown();
            if (!releaseAi.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("AI mock timed out");
            }
            return AiChatResponse.builder().content("ok").build();
        });
        when(chatSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findMaxTurnNumber(SESSION_ID)).thenReturn(0);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<SendChatMessageResponse> first = pool.submit(() ->
                    chatAssistantService.sendMessage(JOB_ID,
                            SendChatMessageRequest.builder().prompt("Hello").build()));
            assertTrue(inAi.await(5, TimeUnit.SECONDS));
            assertThrows(ChatConflictException.class, () ->
                    chatAssistantService.sendMessage(JOB_ID,
                            SendChatMessageRequest.builder().prompt("Hello").build()));
            releaseAi.countDown();
            assertEquals("ok", first.get(5, TimeUnit.SECONDS).getLatestTurn().getAiResponse());
        } finally {
            pool.shutdownNow();
        }
        verify(aiService, times(1)).continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL));
    }

    @Test
    void sendMessage_trimsPriorTurnsToLast16() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build();
        List<ChatMessage> lastSixteen = new ArrayList<>();
        for (int i = 41; i >= 26; i--) {
            lastSixteen.add(ChatMessage.builder()
                    .turnNumber(i)
                    .userPrompt("Q" + i)
                    .aiResponse("A" + i)
                    .build());
        }

        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findRecentByChatSessionId(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(lastSixteen);
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Later answer").build());
        when(chatSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findMaxTurnNumber(SESSION_ID)).thenReturn(41);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Turn 42").build());

        assertEquals(42, response.getLatestTurn().getTurnNumber());
        verify(aiService).continueJobChat(aiRequestCaptor.capture(), eq(USER_EMAIL));
        assertEquals(16, aiRequestCaptor.getValue().getPriorTurns().size());
        assertEquals("Q26", aiRequestCaptor.getValue().getPriorTurns().get(0).getUserPrompt());
        assertEquals("Q41", aiRequestCaptor.getValue().getPriorTurns().get(15).getUserPrompt());
    }

    @Test
    void sendMessage_chatTitle_fallsBackToCompanyWhenTitleNull() {
        setUpAuthenticatedUser();
        JobEntity job = JobEntity.builder().id(JOB_ID).user(testUser).company("Google").title(null).build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(600L);
            return session;
        });
        when(chatMessageRepository.findRecentByChatSessionId(eq(600L), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Answer").build());
        ChatSession persisted = ChatSession.builder().id(600L).job(job).user(testUser).chatTitle("Google").build();
        when(chatSessionRepository.findByIdForUpdate(600L)).thenReturn(Optional.of(persisted));
        when(chatMessageRepository.findMaxTurnNumber(600L)).thenReturn(0);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hi").build());

        assertEquals("Google", response.getChatTitle());
    }

    @Test
    void sendMessage_chatTitle_fallsBackToTitleWhenCompanyNull() {
        setUpAuthenticatedUser();
        JobEntity job = JobEntity.builder().id(JOB_ID).user(testUser).company(null).title("Backend Engineer").build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(601L);
            return session;
        });
        when(chatMessageRepository.findRecentByChatSessionId(eq(601L), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Answer").build());
        ChatSession persisted = ChatSession.builder().id(601L).job(job).user(testUser)
                .chatTitle("Backend Engineer").build();
        when(chatSessionRepository.findByIdForUpdate(601L)).thenReturn(Optional.of(persisted));
        when(chatMessageRepository.findMaxTurnNumber(601L)).thenReturn(0);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hi").build());

        assertEquals("Backend Engineer", response.getChatTitle());
    }

    @Test
    void sendMessage_chatTitle_fallsBackToTitleWhenCompanyBlank() {
        setUpAuthenticatedUser();
        JobEntity job = JobEntity.builder().id(JOB_ID).user(testUser).company("   ").title("ML Engineer").build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(602L);
            return session;
        });
        when(chatMessageRepository.findRecentByChatSessionId(eq(602L), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Answer").build());
        ChatSession persisted = ChatSession.builder().id(602L).job(job).user(testUser).chatTitle("ML Engineer").build();
        when(chatSessionRepository.findByIdForUpdate(602L)).thenReturn(Optional.of(persisted));
        when(chatMessageRepository.findMaxTurnNumber(602L)).thenReturn(0);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hi").build());

        assertEquals("ML Engineer", response.getChatTitle());
    }

    @Test
    void sendMessage_chatTitle_clampsTo255() {
        setUpAuthenticatedUser();
        String company = "C".repeat(255);
        String title = "T".repeat(255);
        JobEntity job = JobEntity.builder().id(JOB_ID).user(testUser).company(company).title(title).build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());
        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(603L);
            return session;
        });
        when(chatMessageRepository.findRecentByChatSessionId(eq(603L), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Answer").build());
        when(chatSessionRepository.findByIdForUpdate(603L)).thenReturn(Optional.of(
                ChatSession.builder().id(603L).job(job).user(testUser).chatTitle("clamped").build()));
        when(chatMessageRepository.findMaxTurnNumber(603L)).thenReturn(0);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatAssistantService.sendMessage(JOB_ID, SendChatMessageRequest.builder().prompt("Hi").build());

        verify(chatSessionRepository, times(2)).save(sessionCaptor.capture());
        String createdTitle = sessionCaptor.getAllValues().get(0).getChatTitle();
        assertEquals(255, createdTitle.length());
        assertTrue(createdTitle.endsWith("..."));
    }

    @Test
    void sendMessage_bothCompanyAndTitleBlank_doesNotNpe() {
        setUpAuthenticatedUser();
        JobEntity job = JobEntity.builder().id(JOB_ID).user(testUser).company("").title("").build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(604L);
            return session;
        });
        when(chatMessageRepository.findRecentByChatSessionId(eq(604L), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Answer").build());
        ChatSession persisted = ChatSession.builder().id(604L).job(job).user(testUser).chatTitle("").build();
        when(chatSessionRepository.findByIdForUpdate(604L)).thenReturn(Optional.of(persisted));
        when(chatMessageRepository.findMaxTurnNumber(604L)).thenReturn(0);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hi").build());

        assertEquals("", response.getChatTitle());
    }

    @Test
    void sendMessage_jobNotOwnedByUser_throwsJobNotFound() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        JobNotFoundException ex = assertThrows(JobNotFoundException.class,
                () -> chatAssistantService.sendMessage(JOB_ID,
                        SendChatMessageRequest.builder().prompt("Hello").build()));
        assertEquals("Job not found.", ex.getMessage());
        verify(aiService, never()).continueJobChat(any(), any());
    }

    @Test
    void sendMessage_currentUserServiceThrows_propagatesInvalidCredentials() {
        when(currentUserService.getCurrentUser())
                .thenThrow(new com.developer.copilot.auth.exception.InvalidCredentialsException("User is not authenticated."));

        assertThrows(com.developer.copilot.auth.exception.InvalidCredentialsException.class,
                () -> chatAssistantService.sendMessage(JOB_ID,
                        SendChatMessageRequest.builder().prompt("Hello").build()));
        verify(jobRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void sendMessage_aiServiceFails_messageIsNeverSaved() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findRecentByChatSessionId(eq(SESSION_ID), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenThrow(new AiServiceException("AI provider unavailable"));

        assertThrows(AiServiceException.class, () -> chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hello").build()));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_firstMessageAiFails_discardsEmptySession() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        stubNewSessionPersist();
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenThrow(new AiServiceException("down"));
        when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(
                ChatSession.builder().id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build()));

        assertThrows(AiServiceException.class, () -> chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hello").build()));

        verify(chatMessageRepository).deleteByChatSessionId(SESSION_ID);
        verify(chatSessionRepository).delete(any(ChatSession.class));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_blankAiContent_doesNotPersist() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findRecentByChatSessionId(eq(SESSION_ID), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("   ").build());

        assertThrows(AiServiceException.class, () -> chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hello").build()));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_stripsScriptTagsFromAiContent() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findRecentByChatSessionId(eq(SESSION_ID), any(Pageable.class))).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder()
                        .content("<script>alert(1)</script>You match this role.")
                        .build());
        when(chatSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findMaxTurnNumber(SESSION_ID)).thenReturn(0);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hello").build());

        assertEquals("You match this role.", response.getLatestTurn().getAiResponse());
    }

    @Test
    void getChatHistory_noSessionYet_returnsEmptyResultNotError() {
        setUpAuthenticatedUser();
        Pageable pageable = PageRequest.of(0, 50);
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        ChatSessionResponse response = chatAssistantService.getChatHistory(JOB_ID, pageable);

        assertNull(response.getChatSessionId());
        assertNull(response.getChatTitle());
        assertEquals(JOB_ID, response.getJobId());
        assertTrue(response.getMessages().isEmpty());
    }

    @Test
    void getChatHistory_populatedSession_returnsOrderedTurns() {
        setUpAuthenticatedUser();
        Pageable pageable = PageRequest.of(0, 50);
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build();
        ChatMessage turn1 = ChatMessage.builder().id(1L).chatSession(existingSession).turnNumber(1)
                .userPrompt("Q1").aiResponse("A1").build();
        ChatMessage turn2 = ChatMessage.builder().id(2L).chatSession(existingSession).turnNumber(2)
                .userPrompt("Q2").aiResponse("A2").build();

        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(SESSION_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(turn1, turn2), pageable, 2));

        ChatSessionResponse response = chatAssistantService.getChatHistory(JOB_ID, pageable);

        assertEquals(SESSION_ID, response.getChatSessionId());
        assertEquals(2, response.getMessages().size());
        assertEquals("Q1", response.getMessages().get(0).getUserPrompt());
        assertEquals(2L, response.getTotalElements());
    }

    @Test
    void getChatHistory_jobNotOwnedByUser_throwsJobNotFound() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class,
                () -> chatAssistantService.getChatHistory(JOB_ID, PageRequest.of(0, 50)));
    }

    @Test
    void listMyChats_returnsSummariesForCurrentUser() {
        setUpAuthenticatedUser();
        ChatSession session1 = ChatSession.builder().id(SESSION_ID).job(testJob).user(testUser)
                .chatTitle("Amazon - SDE 1").build();
        JobEntity anotherJob = JobEntity.builder().id(200L).user(testUser).title("Backend Engineer").company("Google").build();
        ChatSession session2 = ChatSession.builder().id(501L).job(anotherJob).user(testUser)
                .chatTitle("Google - Backend Engineer").build();
        Pageable pageable = PageRequest.of(0, 50);
        when(chatSessionRepository.findAllByUserIdOrderByUpdatedAtDesc(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(session1, session2), pageable, 2));

        ChatSessionListResponse summaries = chatAssistantService.listMyChats(pageable);

        assertEquals(2, summaries.getChats().size());
        assertEquals("Amazon - SDE 1", summaries.getChats().get(0).getChatTitle());
        assertEquals(JOB_ID, summaries.getChats().get(0).getJobId());
        assertEquals(2L, summaries.getTotalElements());
    }

    @Test
    void listMyChats_noChats_returnsEmptyList() {
        setUpAuthenticatedUser();
        Pageable pageable = PageRequest.of(0, 50);
        when(chatSessionRepository.findAllByUserIdOrderByUpdatedAtDesc(USER_ID, pageable))
                .thenReturn(Page.empty(pageable));

        ChatSessionListResponse summaries = chatAssistantService.listMyChats(pageable);

        assertTrue(summaries.getChats().isEmpty());
        assertEquals(0L, summaries.getTotalElements());
    }

    @Test
    void deleteChat_existingSession_deletesMessagesThenSession() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(SESSION_ID).job(testJob).user(testUser).chatTitle("Amazon - SDE 1").build();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(existingSession));

        chatAssistantService.deleteChat(JOB_ID);

        verify(chatMessageRepository, times(1)).deleteByChatSessionId(SESSION_ID);
        verify(chatSessionRepository, times(1)).delete(existingSession);
    }

    @Test
    void deleteChat_noSessionExists_isIdempotentNoOp() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        chatAssistantService.deleteChat(JOB_ID);

        verify(chatMessageRepository, never()).deleteByChatSessionId(anyLong());
        verify(chatSessionRepository, never()).delete(any());
    }

    @Test
    void deleteChat_jobNotOwnedByUser_throwsJobNotFound() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> chatAssistantService.deleteChat(JOB_ID));
        verify(chatSessionRepository, never()).findByJobIdAndUserId(any(), any());
    }

    @Test
    void getChatHistory_unauthenticated_propagates() {
        when(currentUserService.getCurrentUser())
                .thenThrow(new com.developer.copilot.auth.exception.InvalidCredentialsException("User is not authenticated."));
        assertThrows(com.developer.copilot.auth.exception.InvalidCredentialsException.class,
                () -> chatAssistantService.getChatHistory(JOB_ID, PageRequest.of(0, 50)));
    }

    @Test
    void listMyChats_unauthenticated_propagates() {
        when(currentUserService.getCurrentUser())
                .thenThrow(new com.developer.copilot.auth.exception.InvalidCredentialsException("User is not authenticated."));
        assertThrows(com.developer.copilot.auth.exception.InvalidCredentialsException.class,
                () -> chatAssistantService.listMyChats(PageRequest.of(0, 50)));
    }

    @Test
    void deleteChat_unauthenticated_propagates() {
        when(currentUserService.getCurrentUser())
                .thenThrow(new com.developer.copilot.auth.exception.InvalidCredentialsException("User is not authenticated."));
        assertThrows(com.developer.copilot.auth.exception.InvalidCredentialsException.class,
                () -> chatAssistantService.deleteChat(JOB_ID));
    }
}
