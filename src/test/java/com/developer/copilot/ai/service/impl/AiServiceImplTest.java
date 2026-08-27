package com.developer.copilot.ai.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import com.developer.copilot.ai.config.AiProperties;
import com.developer.copilot.ai.dto.request.ChatTurnDto;
import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.context.PromptTemplateService;
import com.developer.copilot.ai.service.context.ResumeContextService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.repository.JobRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private ResumeContextService resumeContextService;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @InjectMocks
    private AiServiceImpl aiService;

    @Captor
    private ArgumentCaptor<List<Message>> messagesCaptor;

    private User testUser;
    private JobEntity testJob;

    private static final String USER_EMAIL = "candidate@example.com";
    private static final Long JOB_ID = 100L;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail(USER_EMAIL);

        testJob = JobEntity.builder()
                .id(JOB_ID)
                .user(testUser)
                .title("SDE 1")
                .company("Amazon")
                .description("Cleaned job description text.")
                .originalDescription("Full pasted job posting text.")
                .build();
    }

    @Test
    void continueJobChat_firstTurn_buildsSystemPromptOnceAndSendsOnlyNewPrompt() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildJobChatSystemPrompt("resume text", "Cleaned job description text."))
                .thenReturn("SYSTEM_PROMPT");
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        stubChatClientChain("AI answer to the first prompt");

        JobChatAiRequest request = JobChatAiRequest.builder()
                .jobId(JOB_ID)
                .priorTurns(List.of())
                .newPrompt("How well do I match this role?")
                .build();

        AiChatResponse response = aiService.continueJobChat(request, USER_EMAIL);

        assertEquals("AI answer to the first prompt", response.getContent());
        assertEquals("gemini-flash-latest", response.getModel());

        verify(promptTemplateService, times(1))
                .buildJobChatSystemPrompt("resume text", "Cleaned job description text.");
        verify(requestSpec, times(1)).system("SYSTEM_PROMPT");
        verify(requestSpec).messages(messagesCaptor.capture());

        List<Message> sentMessages = messagesCaptor.getValue();
        assertEquals(1, sentMessages.size());
        assertTrue(sentMessages.get(0) instanceof UserMessage);
        assertEquals("How well do I match this role?", sentMessages.get(0).getText());
    }

    @Test
    void continueJobChat_withPriorTurns_rebuildsFullHistoryAndBuildsSystemPromptExactlyOnce() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildJobChatSystemPrompt(anyString(), anyString())).thenReturn("SYSTEM_PROMPT");
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        stubChatClientChain("Third answer");

        List<ChatTurnDto> priorTurns = List.of(
                ChatTurnDto.builder().userPrompt("First question").aiResponse("First answer").build(),
                ChatTurnDto.builder().userPrompt("Second question").aiResponse("Second answer").build()
        );

        JobChatAiRequest request = JobChatAiRequest.builder()
                .jobId(JOB_ID)
                .priorTurns(priorTurns)
                .newPrompt("Third question")
                .build();

        AiChatResponse response = aiService.continueJobChat(request, USER_EMAIL);

        assertEquals("Third answer", response.getContent());

        // System/resume/job-description context is assembled exactly once per call, never per turn.
        verify(promptTemplateService, times(1)).buildJobChatSystemPrompt(anyString(), anyString());

        verify(requestSpec).messages(messagesCaptor.capture());
        List<Message> sentMessages = messagesCaptor.getValue();

        assertEquals(5, sentMessages.size());
        assertTrue(sentMessages.get(0) instanceof UserMessage);
        assertEquals("First question", sentMessages.get(0).getText());
        assertTrue(sentMessages.get(1) instanceof AssistantMessage);
        assertEquals("First answer", sentMessages.get(1).getText());
        assertTrue(sentMessages.get(2) instanceof UserMessage);
        assertEquals("Second question", sentMessages.get(2).getText());
        assertTrue(sentMessages.get(3) instanceof AssistantMessage);
        assertEquals("Second answer", sentMessages.get(3).getText());
        assertTrue(sentMessages.get(4) instanceof UserMessage);
        assertEquals("Third question", sentMessages.get(4).getText());
    }

    @Test
    void continueJobChat_aiCallFails_throwsAiServiceException() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildJobChatSystemPrompt(anyString(), anyString())).thenReturn("SYSTEM_PROMPT");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("upstream timed out"));

        JobChatAiRequest request = JobChatAiRequest.builder()
                .jobId(JOB_ID)
                .priorTurns(List.of())
                .newPrompt("Any question")
                .build();

        assertThrows(AiServiceException.class, () -> aiService.continueJobChat(request, USER_EMAIL));
    }

    private void stubChatClientChain(String assistantReplyText) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(assistantReplyText))));
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
    }
}
