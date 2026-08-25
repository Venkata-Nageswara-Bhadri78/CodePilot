package com.developer.copilot.chatassistant.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.exception.AiServiceException;
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
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.repository.JobRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    @Spy
    private ChatAssistantMapper chatAssistantMapper = new ChatAssistantMapper();

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

    @Test
    void sendMessage_firstMessage_createsSessionWithDeterministicTitle() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(500L);
            return session;
        });
        when(chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(500L)).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("You're a strong match for this role.").build());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageRequest request = SendChatMessageRequest.builder()
                .prompt("How well do I match this role?")
                .build();

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID, request);

        assertEquals(500L, response.getChatSessionId());
        assertEquals("Amazon - SDE 1", response.getChatTitle());
        assertEquals("You're a strong match for this role.", response.getLatestTurn().getAiResponse());
        assertEquals(1, response.getLatestTurn().getTurnNumber());

        verify(chatSessionRepository, times(1)).save(any(ChatSession.class));
        verify(chatMessageRepository).save(chatMessageCaptor.capture());
        assertEquals(1, chatMessageCaptor.getValue().getTurnNumber());
        assertEquals("How well do I match this role?", chatMessageCaptor.getValue().getUserPrompt());

        verify(aiService).continueJobChat(aiRequestCaptor.capture(), eq(USER_EMAIL));
        assertTrue(aiRequestCaptor.getValue().getPriorTurns().isEmpty());
    }

    @Test
    void sendMessage_secondMessage_reusesSessionAndIncrementsTurnNumber() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(500L)
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
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(500L)).thenReturn(List.of(firstTurn));
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Second answer").build());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageRequest request = SendChatMessageRequest.builder()
                .prompt("Second question")
                .build();

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID, request);

        assertEquals(2, response.getLatestTurn().getTurnNumber());
        verify(chatSessionRepository, never()).save(any(ChatSession.class));

        verify(aiService).continueJobChat(aiRequestCaptor.capture(), eq(USER_EMAIL));
        assertEquals(1, aiRequestCaptor.getValue().getPriorTurns().size());
        assertEquals("First question", aiRequestCaptor.getValue().getPriorTurns().get(0).getUserPrompt());
    }

    @Test
    void sendMessage_chatTitle_fallsBackToCompanyWhenTitleNull() {
        setUpAuthenticatedUser();
        JobEntity job = JobEntity.builder().id(JOB_ID).user(testUser).company("Google").title(null).build();

        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(600L);
            return session;
        });
        when(chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(600L)).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Answer").build());
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
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(601L);
            return session;
        });
        when(chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(601L)).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Answer").build());
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
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(602L);
            return session;
        });
        when(chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(602L)).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenReturn(AiChatResponse.builder().content("Answer").build());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendChatMessageResponse response = chatAssistantService.sendMessage(JOB_ID,
                SendChatMessageRequest.builder().prompt("Hi").build());

        assertEquals("ML Engineer", response.getChatTitle());
    }

    @Test
    void sendMessage_jobNotOwnedByUser_throwsJobNotFound() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        SendChatMessageRequest request = SendChatMessageRequest.builder().prompt("Hello").build();

        assertThrows(JobNotFoundException.class, () -> chatAssistantService.sendMessage(JOB_ID, request));
        verify(aiService, never()).continueJobChat(any(), any());
    }

    @Test
    void sendMessage_currentUserServiceThrows_propagatesInvalidCredentials() {
        when(currentUserService.getCurrentUser())
                .thenThrow(new com.developer.copilot.auth.exception.InvalidCredentialsException("User is not authenticated."));

        SendChatMessageRequest request = SendChatMessageRequest.builder().prompt("Hello").build();

        assertThrows(com.developer.copilot.auth.exception.InvalidCredentialsException.class,
                () -> chatAssistantService.sendMessage(JOB_ID, request));
        verify(jobRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void sendMessage_aiServiceFails_messageIsNeverSaved() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));

        ChatSession existingSession = ChatSession.builder()
                .id(500L)
                .job(testJob)
                .user(testUser)
                .chatTitle("Amazon - SDE 1")
                .build();
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(500L)).thenReturn(List.of());
        when(aiService.continueJobChat(any(JobChatAiRequest.class), eq(USER_EMAIL)))
                .thenThrow(new AiServiceException("AI provider unavailable"));

        SendChatMessageRequest request = SendChatMessageRequest.builder().prompt("Hello").build();

        assertThrows(AiServiceException.class, () -> chatAssistantService.sendMessage(JOB_ID, request));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void getChatHistory_noSessionYet_returnsEmptyResultNotError() {
        setUpAuthenticatedUser();
        Pageable pageable = PageRequest.of(0, 50);
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.empty());

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
                .id(500L)
                .job(testJob)
                .user(testUser)
                .chatTitle("Amazon - SDE 1")
                .build();

        ChatMessage turn1 = ChatMessage.builder().id(1L).chatSession(existingSession).turnNumber(1)
                .userPrompt("Q1").aiResponse("A1").build();
        ChatMessage turn2 = ChatMessage.builder().id(2L).chatSession(existingSession).turnNumber(2)
                .userPrompt("Q2").aiResponse("A2").build();

        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(existingSession));
        Page<ChatMessage> page = new PageImpl<>(List.of(turn1, turn2), pageable, 2);
        when(chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(500L, pageable)).thenReturn(page);

        ChatSessionResponse response = chatAssistantService.getChatHistory(JOB_ID, pageable);

        assertEquals(500L, response.getChatSessionId());
        assertEquals("Amazon - SDE 1", response.getChatTitle());
        assertEquals(2, response.getMessages().size());
        assertEquals("Q1", response.getMessages().get(0).getUserPrompt());
        assertEquals("Q2", response.getMessages().get(1).getUserPrompt());
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
        ChatSession session1 = ChatSession.builder().id(500L).job(testJob).user(testUser)
                .chatTitle("Amazon - SDE 1").build();

        JobEntity anotherJob = JobEntity.builder().id(200L).user(testUser).title("Backend Engineer").company("Google").build();
        ChatSession session2 = ChatSession.builder().id(501L).job(anotherJob).user(testUser)
                .chatTitle("Google - Backend Engineer").build();

        when(chatSessionRepository.findAllByUserIdWithJob(USER_ID)).thenReturn(List.of(session1, session2));

        List<ChatSessionSummaryResponse> summaries = chatAssistantService.listMyChats();

        assertEquals(2, summaries.size());
        assertEquals("Amazon - SDE 1", summaries.get(0).getChatTitle());
        assertEquals(JOB_ID, summaries.get(0).getJobId());
        assertEquals("Google - Backend Engineer", summaries.get(1).getChatTitle());
    }

    @Test
    void listMyChats_noChats_returnsEmptyList() {
        setUpAuthenticatedUser();
        when(chatSessionRepository.findAllByUserIdWithJob(USER_ID)).thenReturn(List.of());

        List<ChatSessionSummaryResponse> summaries = chatAssistantService.listMyChats();

        assertTrue(summaries.isEmpty());
    }

    @Test
    void deleteChat_existingSession_deletesMessagesThenSession() {
        setUpAuthenticatedUser();
        ChatSession existingSession = ChatSession.builder()
                .id(500L)
                .job(testJob)
                .user(testUser)
                .chatTitle("Amazon - SDE 1")
                .build();

        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.of(existingSession));

        chatAssistantService.deleteChat(JOB_ID);

        verify(chatMessageRepository, times(1)).deleteByChatSessionId(500L);
        verify(chatSessionRepository, times(1)).delete(existingSession);
    }

    @Test
    void deleteChat_noSessionExists_isIdempotentNoOp() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.empty());

        chatAssistantService.deleteChat(JOB_ID);

        verify(chatMessageRepository, never()).deleteByChatSessionId(anyLong());
        verify(chatSessionRepository, never()).delete(any());
    }

    @Test
    void deleteChat_jobNotOwnedByUser_throwsJobNotFound() {
        setUpAuthenticatedUser();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> chatAssistantService.deleteChat(JOB_ID));
        verify(chatSessionRepository, never()).findByJobId(any());
    }
}
