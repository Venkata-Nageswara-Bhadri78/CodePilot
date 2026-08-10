package com.developer.copilot.chatassistant.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
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
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.repository.JobRepository;

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
    private UserRepository userRepository;

    @Mock
    private AiService aiService;

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

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setEmail(USER_EMAIL);

        testJob = JobEntity.builder()
                .id(JOB_ID)
                .user(testUser)
                .title("SDE 1")
                .company("Amazon")
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(USER_EMAIL, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendMessage_firstMessage_createsSessionWithDeterministicTitle() {
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
    void sendMessage_jobNotOwnedByUser_throwsJobNotFound() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        SendChatMessageRequest request = SendChatMessageRequest.builder().prompt("Hello").build();

        assertThrows(JobNotFoundException.class, () -> chatAssistantService.sendMessage(JOB_ID, request));
        verify(aiService, never()).continueJobChat(any(), any());
    }

    @Test
    void getChatHistory_noSessionYet_returnsEmptyResultNotError() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.empty());

        ChatSessionResponse response = chatAssistantService.getChatHistory(JOB_ID);

        assertNull(response.getChatSessionId());
        assertNull(response.getChatTitle());
        assertEquals(JOB_ID, response.getJobId());
        assertTrue(response.getMessages().isEmpty());
    }

    @Test
    void getChatHistory_populatedSession_returnsOrderedTurns() {
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
        when(chatMessageRepository.findAllByChatSessionIdOrderByTurnNumberAsc(500L)).thenReturn(List.of(turn1, turn2));

        ChatSessionResponse response = chatAssistantService.getChatHistory(JOB_ID);

        assertEquals(500L, response.getChatSessionId());
        assertEquals("Amazon - SDE 1", response.getChatTitle());
        assertEquals(2, response.getMessages().size());
        assertEquals("Q1", response.getMessages().get(0).getUserPrompt());
        assertEquals("Q2", response.getMessages().get(1).getUserPrompt());
    }

    @Test
    void getChatHistory_jobNotOwnedByUser_throwsJobNotFound() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> chatAssistantService.getChatHistory(JOB_ID));
    }

    @Test
    void listMyChats_returnsSummariesForCurrentUser() {
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
    void deleteChat_existingSession_deletesMessagesThenSession() {
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
    void deleteChat_noSessionExists_throwsChatSessionNotFound() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(chatSessionRepository.findByJobId(JOB_ID)).thenReturn(Optional.empty());

        assertThrows(ChatSessionNotFoundException.class, () -> chatAssistantService.deleteChat(JOB_ID));
        verify(chatMessageRepository, never()).deleteByChatSessionId(anyLong());
    }

    @Test
    void deleteChat_jobNotOwnedByUser_throwsJobNotFound() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> chatAssistantService.deleteChat(JOB_ID));
        verify(chatSessionRepository, never()).findByJobId(any());
    }
}
