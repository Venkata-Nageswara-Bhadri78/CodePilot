package com.developer.copilot.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.ai.chat.prompt.ChatOptions;

import com.developer.copilot.ai.config.AiProperties;
import com.developer.copilot.ai.dto.request.AiChatRequest;
import com.developer.copilot.ai.dto.request.AiMode;
import com.developer.copilot.ai.dto.request.ChatTurnDto;
import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.request.JobExtractionAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.context.PromptTemplateService;
import com.developer.copilot.ai.service.context.ResumeContextService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.repository.JobRepository;

import reactor.core.publisher.Flux;

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

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

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
    void chat_minimalRequest_mapsResponseMetadata() {
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildSystemPrompt(AiMode.GENERAL_CHAT)).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("Generated answer");

        AiChatResponse response = aiService.chat(AiChatRequest.builder()
                .prompt("Help me")
                .mode(AiMode.GENERAL_CHAT)
                .build(), USER_EMAIL);

        assertEquals("Generated answer", response.getContent());
        assertEquals("gemini-flash-latest", response.getModel());
        assertEquals(AiMode.GENERAL_CHAT, response.getMode());
        assertEquals("STOP", response.getFinishReason());
        assertTrue(response.getTimestamp() != null);
    }

    @ParameterizedTest
    @EnumSource(AiMode.class)
    void chat_allModes_buildSystemPromptForMode(AiMode mode) {
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(mode)).thenReturn("SYSTEM-" + mode);
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), eq(mode)))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        AiChatResponse response = aiService.chat(AiChatRequest.builder()
                .prompt("prompt")
                .mode(mode)
                .build(), USER_EMAIL);

        assertEquals(mode, response.getMode());
        verify(promptTemplateService).buildSystemPrompt(mode);
    }

    @Test
    void chat_customResumeAndInlineJob_overrideIds() {
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(eq("Q"), eq("custom resume"), eq("inline JD"), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        aiService.chat(AiChatRequest.builder()
                .prompt("Q")
                .customResumeText("custom resume")
                .resumeId(5L)
                .jobDescription("inline JD")
                .jobId(JOB_ID)
                .build(), USER_EMAIL);

        verify(resumeContextService, never()).getResumeContext(any());
        verify(jobRepository, never()).findByIdAndUserId(any(), any());
        verify(promptTemplateService).buildUserMessage("Q", "custom resume", "inline JD", AiMode.GENERAL_CHAT);
    }

    @Test
    void chat_ownedJob_prefersDescriptionThenOriginal() {
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), eq("Cleaned job description text."), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        aiService.chat(AiChatRequest.builder().prompt("Q").jobId(JOB_ID).build(), USER_EMAIL);

        verify(jobRepository).findByIdAndUserId(JOB_ID, testUser.getId());
    }

    @Test
    void chat_missingJob_throwsNotFoundAndDoesNotCallProvider() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(999L, testUser.getId())).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").jobId(999L).build(), USER_EMAIL));

        verify(chatClient, never()).prompt();
    }

    @Test
    void chat_unknownProviderError_returnsGenericMessage() {
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("secret-project-id-xyz leaked"));

        AiServiceException ex = assertThrows(AiServiceException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").build(), USER_EMAIL));

        assertFalse(ex.getMessage().contains("secret-project-id-xyz"));
        assertTrue(ex.getMessage().contains("unexpected error"));
    }

    @Test
    void streamChat_filtersEmptyTokensAndCompletesWithStop() {
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("Hello", "", " world"));

        List<AiStreamChunk> chunks = aiService.streamChat(
                AiChatRequest.builder().prompt("Q").build(), USER_EMAIL).collectList().block();

        assertEquals(3, chunks.size());
        assertEquals("Hello", chunks.get(0).getContent());
        assertFalse(chunks.get(0).isCompleted());
        assertEquals(" world", chunks.get(1).getContent());
        assertTrue(chunks.get(2).isCompleted());
        assertEquals("STOP", chunks.get(2).getFinishReason());
    }

    @Test
    void streamChat_providerFailure_emitsTerminalError() {
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.error(new RuntimeException("provider down")));

        List<AiStreamChunk> chunks = aiService.streamChat(
                AiChatRequest.builder().prompt("Q").build(), USER_EMAIL).collectList().block();

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).isCompleted());
        assertEquals("ERROR", chunks.get(0).getFinishReason());
        assertFalse(chunks.get(0).getContent().contains("provider down"));
    }

    @Test
    void streamChat_timeout_emitsTerminalError() {
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(1);
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("slow").delayElements(Duration.ofSeconds(3)));

        List<AiStreamChunk> chunks = aiService.streamChat(
                AiChatRequest.builder().prompt("Q").build(), USER_EMAIL).collectList().block();

        assertEquals(1, chunks.size());
        assertEquals("ERROR", chunks.get(0).getFinishReason());
    }

    @Test
    void extractJobInfo_returnsStructuredEntity() {
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        when(promptTemplateService.buildJobExtractionSystemPrompt()).thenReturn("EXTRACT");
        when(promptTemplateService.buildJobExtractionUserMessage(anyString(), anyString())).thenReturn("RAW");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(JobExtractionAiResponse.class)).thenReturn(
                JobExtractionAiResponse.builder().title("Engineer").industry("").sourcePlatform("").build());

        JobExtractionAiResponse result = aiService.extractJobInfo(JobExtractionAiRequest.builder()
                .jobUrl("https://example.com/job")
                .rawJobText("Engineer role")
                .build());

        assertEquals("Engineer", result.getTitle());
        assertEquals("", result.getIndustry());
    }

    @Test
    void continueJobChat_firstTurn_buildsSystemPromptOnceAndSendsOnlyNewPrompt() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildJobChatSystemPrompt("resume text", "Cleaned job description text."))
                .thenReturn("SYSTEM_PROMPT");
        when(aiProperties.getDefaultModel()).thenReturn("gemini-flash-latest");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        stubJobChatClientChain("AI answer to the first prompt");

        JobChatAiRequest request = JobChatAiRequest.builder()
                .jobId(JOB_ID)
                .priorTurns(List.of())
                .newPrompt("How well do I match this role?")
                .build();

        AiChatResponse response = aiService.continueJobChat(request, USER_EMAIL);

        assertEquals("AI answer to the first prompt", response.getContent());
        assertEquals("gemini-flash-latest", response.getModel());
        assertEquals(AiMode.GENERAL_CHAT, response.getMode());

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
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        stubJobChatClientChain("Third answer");

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
        verify(promptTemplateService, times(1)).buildJobChatSystemPrompt(anyString(), anyString());
        verify(requestSpec).messages(messagesCaptor.capture());
        List<Message> sentMessages = messagesCaptor.getValue();

        assertEquals(5, sentMessages.size());
        assertTrue(sentMessages.get(0) instanceof UserMessage);
        assertEquals("First question", sentMessages.get(0).getText());
        assertTrue(sentMessages.get(1) instanceof AssistantMessage);
        assertEquals("First answer", sentMessages.get(1).getText());
        assertTrue(sentMessages.get(4) instanceof UserMessage);
        assertEquals("Third question", sentMessages.get(4).getText());
    }

    @Test
    void continueJobChat_blankPriorTurn_rejectsBeforeProviderCall() {
        JobChatAiRequest request = JobChatAiRequest.builder()
                .jobId(JOB_ID)
                .priorTurns(List.of(ChatTurnDto.builder().userPrompt(" ").aiResponse("answer").build()))
                .newPrompt("Next")
                .build();

        assertThrows(IllegalArgumentException.class, () -> aiService.continueJobChat(request, USER_EMAIL));
        verify(chatClient, never()).prompt();
    }

    @Test
    void continueJobChat_aiCallFails_throwsAiServiceException() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildJobChatSystemPrompt(anyString(), anyString())).thenReturn("SYSTEM_PROMPT");
        when(aiProperties.getTimeoutSeconds()).thenReturn(60);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("upstream timed out"));

        JobChatAiRequest request = JobChatAiRequest.builder()
                .jobId(JOB_ID)
                .priorTurns(List.of())
                .newPrompt("Any question")
                .build();

        assertThrows(AiServiceException.class, () -> aiService.continueJobChat(request, USER_EMAIL));
    }

    @Test
    void continueJobChat_foreignJob_throwsNotFound() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.empty());
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");

        assertThrows(JobNotFoundException.class, () -> aiService.continueJobChat(
                JobChatAiRequest.builder().jobId(JOB_ID).newPrompt("Q").build(), USER_EMAIL));
        verify(chatClient, never()).prompt();
    }

    private void stubSyncChatClientChain(String assistantReplyText) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(assistantReplyText))));
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
    }

    private void stubJobChatClientChain(String assistantReplyText) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(assistantReplyText))));
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
    }
}
