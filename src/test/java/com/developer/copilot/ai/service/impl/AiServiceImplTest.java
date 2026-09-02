package com.developer.copilot.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

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
import com.developer.copilot.ai.exception.AiUnavailableException;
import com.developer.copilot.ai.metrics.AiMetrics;
import com.developer.copilot.ai.resilience.AiChatGuard;
import com.developer.copilot.ai.service.context.PromptTemplateService;
import com.developer.copilot.ai.service.context.ResumeContextService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.repository.JobRepository;
import com.developer.copilot.user.exception.ResumeNotFoundException;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private ResumeContextService resumeContextService;

    @Spy
    private AiProperties aiProperties = new AiProperties();

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiChatGuard aiChatGuard;

    @Mock
    private AiMetrics aiMetrics;

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
                .description("Cleaned job description text.")
                .originalDescription("Full pasted job posting text.")
                .build();

        lenient().doAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            return callable.call();
        }).when(aiChatGuard).call(any());
        lenient().doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        }).when(aiChatGuard).guardStream(any());
    }

    @Test
    void chat_minimalRequest_mapsResponseMetadata() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildSystemPrompt(AiMode.GENERAL_CHAT)).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("Generated answer");

        AiChatResponse response = aiService.chat(AiChatRequest.builder()
                .prompt("Help me")
                .mode(AiMode.GENERAL_CHAT)
                .build(), USER_ID);

        assertEquals("Generated answer", response.getContent());
        assertEquals("gemini-flash-latest", response.getModel());
        assertEquals(AiMode.GENERAL_CHAT, response.getMode());
        assertEquals("STOP", response.getFinishReason());
        assertTrue(response.getTimestamp() != null);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @ParameterizedTest
    @EnumSource(AiMode.class)
    void chat_allModes_buildSystemPromptForMode(AiMode mode) {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(mode)).thenReturn("SYSTEM-" + mode);
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), eq(mode)))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        AiChatResponse response = aiService.chat(AiChatRequest.builder()
                .prompt("prompt")
                .mode(mode)
                .build(), USER_ID);

        assertEquals(mode, response.getMode());
        verify(promptTemplateService).buildSystemPrompt(mode);
    }

    @Test
    void chat_customResumeAndInlineJob_overrideIds() {
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
                .build(), USER_ID);

        verify(resumeContextService, never()).getResumeContext(any());
        verify(jobRepository, never()).findByIdAndUserId(any(), any());
        verify(promptTemplateService).buildUserMessage("Q", "custom resume", "inline JD", AiMode.GENERAL_CHAT);
    }

    @Test
    void chat_ownedJob_prefersDescriptionThenOriginal() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), eq("Cleaned job description text."), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        aiService.chat(AiChatRequest.builder().prompt("Q").jobId(JOB_ID).build(), USER_ID);

        verify(jobRepository).findByIdAndUserId(JOB_ID, USER_ID);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void chat_blankDescription_fallsBackToOriginalDescription() {
        testJob.setDescription("  ");
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), eq("Full pasted job posting text."), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        aiService.chat(AiChatRequest.builder().prompt("Q").jobId(JOB_ID).build(), USER_ID);

        verify(promptTemplateService).buildUserMessage("Q", "resume", "Full pasted job posting text.", AiMode.GENERAL_CHAT);
    }

    @Test
    void chat_blankJobTexts_stillCallsProviderWithEmptyJd() {
        testJob.setDescription(null);
        testJob.setOriginalDescription("  ");
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(testJob));
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), eq(""), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        aiService.chat(AiChatRequest.builder().prompt("Q").jobId(JOB_ID).build(), USER_ID);

        verify(chatClient).prompt();
        verify(promptTemplateService).buildUserMessage("Q", "resume", "", AiMode.GENERAL_CHAT);
    }

    @Test
    void chat_missingJob_throwsNotFoundAndDoesNotCallProvider() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(jobRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

        JobNotFoundException ex = assertThrows(JobNotFoundException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").jobId(999L).build(), USER_ID));

        assertEquals("Job not found.", ex.getMessage());
        verify(chatClient, never()).prompt();
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void chat_nullUserIdWithJobId_throwsNotFound() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");

        assertThrows(JobNotFoundException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").jobId(JOB_ID).build(), null));
        verify(chatClient, never()).prompt();
        verify(jobRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void chat_missingStoredResume_continuesWithEmptyContext() {
        when(resumeContextService.getResumeContext(null)).thenThrow(new ResumeNotFoundException());
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(eq("Q"), eq(""), eq(""), any())).thenReturn("USER");
        stubSyncChatClientChain("career advice");

        AiChatResponse response = aiService.chat(AiChatRequest.builder().prompt("Q").build(), USER_ID);

        assertEquals("career advice", response.getContent());
        verify(aiMetrics).recordMissingResume();
        verify(chatClient).prompt();
    }

    @Test
    void chat_explicitResumeIdMissing_throwsNotFound() {
        when(resumeContextService.getResumeContext(5L)).thenThrow(new ResumeNotFoundException());

        assertThrows(ResumeNotFoundException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").resumeId(5L).build(), USER_ID));
        verify(chatClient, never()).prompt();
    }

    @Test
    void chat_unknownProviderError_returnsGenericMessage() {
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
                AiChatRequest.builder().prompt("Q").build(), USER_ID));

        assertFalse(ex.getMessage().contains("secret-project-id-xyz"));
        assertTrue(ex.getMessage().contains("unexpected error"));
        assertFalse(ex.getMessage().contains("application.properties"));
    }

    @Test
    void chat_provider401_doesNotLeakConfigPaths() {
        stubFailingProviderCall("401 UNAUTHENTICATED invalid authentication");

        AiServiceException ex = assertThrows(AiServiceException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").build(), USER_ID));

        assertEquals("AI provider authentication failed. Please try again later.", ex.getMessage());
        assertFalse(ex.getMessage().contains("GEMINI_API_KEY"));
        assertFalse(ex.getMessage().contains("application.properties"));
    }

    @Test
    void chat_provider404_doesNotLeakConfigPaths() {
        stubFailingProviderCall("404 NOT_FOUND");

        AiServiceException ex = assertThrows(AiServiceException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").build(), USER_ID));

        assertEquals("The configured AI model is unavailable.", ex.getMessage());
        assertFalse(ex.getMessage().contains("app.ai.default-model"));
        assertFalse(ex.getMessage().contains("application.properties"));
    }

    @Test
    void chat_providerQuota_mapsFriendlyMessage() {
        stubFailingProviderCall("429 RESOURCE_EXHAUSTED quota");

        AiServiceException ex = assertThrows(AiServiceException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").build(), USER_ID));

        assertTrue(ex.getMessage().toLowerCase().contains("quota"));
    }

    @Test
    void chat_providerTimeout_mapsFriendlyMessage() {
        stubFailingProviderCall("TimeoutException timed out Timeout");

        AiServiceException ex = assertThrows(AiServiceException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").build(), USER_ID));

        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    void chat_providerHighDemand_mapsFriendlyMessage() {
        stubFailingProviderCall("503 UNAVAILABLE high demand");

        AiServiceException ex = assertThrows(AiServiceException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").build(), USER_ID));

        assertTrue(ex.getMessage().contains("high demand"));
    }

    @Test
    void chat_setsMaxTokensOnOptions() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        aiService.chat(AiChatRequest.builder().prompt("Q").build(), USER_ID);

        OpenAiChatOptions options = capturedOptions();
        assertEquals(2048, options.getMaxTokens());
        assertEquals("gemini-flash-latest", options.getModel());
    }

    @Test
    void chat_coverLetter_usesHigherMaxTokens() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(AiMode.COVER_LETTER)).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), eq(AiMode.COVER_LETTER)))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        aiService.chat(AiChatRequest.builder().prompt("Q").mode(AiMode.COVER_LETTER).build(), USER_ID);

        assertEquals(4096, capturedOptions().getMaxTokens());
    }

    @Test
    void chat_temperatureProvided_isAppliedToOptions() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        aiService.chat(AiChatRequest.builder().prompt("Q").temperature(0.7).build(), USER_ID);

        assertEquals(0.7, capturedOptions().getTemperature());
    }

    @Test
    void chat_nullTemperature_doesNotSetTemperature() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        stubSyncChatClientChain("ok");

        aiService.chat(AiChatRequest.builder().prompt("Q").build(), USER_ID);

        assertTrue(capturedOptions().getTemperature() == null);
    }

    @Test
    void chat_guardUnavailable_propagates503() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        doThrow(new AiUnavailableException("The AI service is temporarily unavailable. Please try again shortly."))
                .when(aiChatGuard).call(any());

        assertThrows(AiUnavailableException.class, () -> aiService.chat(
                AiChatRequest.builder().prompt("Q").build(), USER_ID));
        verify(chatClient, never()).prompt();
    }

    @Test
    void streamChat_filtersEmptyTokensAndCompletesWithStop() {
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
                AiChatRequest.builder().prompt("Q").build(), USER_ID).collectList().block();

        assertEquals(3, chunks.size());
        assertEquals("Hello", chunks.get(0).getContent());
        assertFalse(chunks.get(0).isCompleted());
        assertEquals(" world", chunks.get(1).getContent());
        assertTrue(chunks.get(2).isCompleted());
        assertEquals("STOP", chunks.get(2).getFinishReason());
    }

    @Test
    void streamChat_providerFailure_emitsTerminalError() {
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
                AiChatRequest.builder().prompt("Q").build(), USER_ID).collectList().block();

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).isCompleted());
        assertEquals("ERROR", chunks.get(0).getFinishReason());
        assertFalse(chunks.get(0).getContent().contains("provider down"));
        assertTrue(chunks.get(0).getContent().contains("AI Service Error:"));
        assertFalse(chunks.get(0).getContent().contains("application.properties"));
    }

    @Test
    void streamChat_timeout_emitsTerminalError() {
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
                AiChatRequest.builder().prompt("Q").build(), USER_ID).collectList().block();

        assertEquals(1, chunks.size());
        assertEquals("ERROR", chunks.get(0).getFinishReason());
    }

    @Test
    void streamChat_missingResumeId_propagatesBeforeProvider() {
        when(resumeContextService.getResumeContext(9L)).thenThrow(new ResumeNotFoundException());

        assertThrows(ResumeNotFoundException.class, () -> aiService.streamChat(
                AiChatRequest.builder().prompt("Q").resumeId(9L).build(), USER_ID));
        verify(chatClient, never()).prompt();
    }

    @Test
    void streamChat_missingJob_propagatesBeforeProvider() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(jobRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> aiService.streamChat(
                AiChatRequest.builder().prompt("Q").jobId(999L).build(), USER_ID));
        verify(chatClient, never()).prompt();
    }

    @Test
    void getResumeContext_usesHighPriorityResume() {
        when(resumeContextService.getResumeContext(null)).thenReturn("parsed");

        assertEquals("parsed", aiService.getResumeContext());
        verify(resumeContextService).getResumeContext(null);
    }

    @Test
    void getActiveModel_includesProvider() {
        assertEquals("gemini-flash-latest (Provider: gemini)", aiService.getActiveModel());
    }

    @Test
    void extractJobInfo_returnsStructuredEntity() {
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
        verify(userRepository, never()).findByEmail(anyString());
        verify(resumeContextService, never()).getResumeContext(any());
    }

    @Test
    void extractJobInfo_nullEntity_throwsAiServiceException() {
        when(promptTemplateService.buildJobExtractionSystemPrompt()).thenReturn("EXTRACT");
        when(promptTemplateService.buildJobExtractionUserMessage(anyString(), anyString())).thenReturn("RAW");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(JobExtractionAiResponse.class)).thenReturn(null);

        assertThrows(AiServiceException.class, () -> aiService.extractJobInfo(JobExtractionAiRequest.builder()
                .jobUrl("https://example.com/job")
                .rawJobText("Engineer role")
                .build()));
    }

    @Test
    void extractJobInfo_providerThrow_isGenericAiServiceException() {
        when(promptTemplateService.buildJobExtractionSystemPrompt()).thenReturn("EXTRACT");
        when(promptTemplateService.buildJobExtractionUserMessage(anyString(), anyString())).thenReturn("RAW");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("internal model dump"));

        AiServiceException ex = assertThrows(AiServiceException.class, () -> aiService.extractJobInfo(
                JobExtractionAiRequest.builder()
                        .jobUrl("https://example.com/job")
                        .rawJobText("Engineer role")
                        .build()));
        assertFalse(ex.getMessage().contains("internal model dump"));
        verify(chatClient).prompt();
    }

    @Test
    void extractJobInfo_setsTemperatureZeroAndMaxTokens() {
        when(promptTemplateService.buildJobExtractionSystemPrompt()).thenReturn("EXTRACT");
        when(promptTemplateService.buildJobExtractionUserMessage(anyString(), anyString())).thenReturn("RAW");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(JobExtractionAiResponse.class)).thenReturn(
                JobExtractionAiResponse.builder().title("Engineer").build());

        aiService.extractJobInfo(JobExtractionAiRequest.builder()
                .jobUrl("https://example.com/job")
                .rawJobText("Engineer role")
                .build());

        OpenAiChatOptions options = capturedOptions();
        assertEquals(0.0, options.getTemperature());
        assertEquals(2048, options.getMaxTokens());
        verify(promptTemplateService).buildJobExtractionSystemPrompt();
        verify(promptTemplateService).buildJobExtractionUserMessage("https://example.com/job", "Engineer role");
    }

    @Test
    void continueJobChat_firstTurn_buildsSystemPromptOnceAndSendsOnlyNewPrompt() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildJobChatSystemPrompt("resume text", "Cleaned job description text."))
                .thenReturn("SYSTEM_PROMPT");
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
    void continueJobChat_trimsHistoryToMaxPriorTurnsSent() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildJobChatSystemPrompt(anyString(), anyString())).thenReturn("SYSTEM_PROMPT");
        stubJobChatClientChain("ok");

        List<ChatTurnDto> priorTurns = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            priorTurns.add(ChatTurnDto.builder().userPrompt("Q" + i).aiResponse("A" + i).build());
        }

        aiService.continueJobChat(JobChatAiRequest.builder()
                .jobId(JOB_ID)
                .priorTurns(priorTurns)
                .newPrompt("Next")
                .build(), USER_EMAIL);

        verify(requestSpec).messages(messagesCaptor.capture());
        List<Message> sent = messagesCaptor.getValue();
        assertEquals(33, sent.size());
        assertEquals("Q5", sent.get(0).getText());
        assertEquals("Next", sent.get(32).getText());
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
    void continueJobChat_fortyOneTurns_rejectsBeforeProviderCall() {
        List<ChatTurnDto> turns = new ArrayList<>();
        for (int i = 0; i < 41; i++) {
            turns.add(ChatTurnDto.builder().userPrompt("Q" + i).aiResponse("A" + i).build());
        }

        assertThrows(IllegalArgumentException.class, () -> aiService.continueJobChat(
                JobChatAiRequest.builder().jobId(JOB_ID).priorTurns(turns).newPrompt("Next").build(),
                USER_EMAIL));
        verify(chatClient, never()).prompt();
    }

    @Test
    void continueJobChat_nullPriorTurns_sendsOnlyNewPrompt() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(promptTemplateService.buildJobChatSystemPrompt(anyString(), anyString())).thenReturn("SYSTEM_PROMPT");
        stubJobChatClientChain("ok");

        aiService.continueJobChat(JobChatAiRequest.builder()
                .jobId(JOB_ID)
                .priorTurns(null)
                .newPrompt("Hello")
                .build(), USER_EMAIL);

        verify(requestSpec).messages(messagesCaptor.capture());
        assertEquals(1, messagesCaptor.getValue().size());
        assertEquals("Hello", messagesCaptor.getValue().get(0).getText());
    }

    @Test
    void continueJobChat_customResume_skipsResumeService() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(jobRepository.findByIdAndUserId(JOB_ID, testUser.getId())).thenReturn(Optional.of(testJob));
        when(promptTemplateService.buildJobChatSystemPrompt(eq("pasted"), anyString())).thenReturn("SYSTEM_PROMPT");
        stubJobChatClientChain("ok");

        aiService.continueJobChat(JobChatAiRequest.builder()
                .jobId(JOB_ID)
                .customResumeText("pasted")
                .newPrompt("Q")
                .build(), USER_EMAIL);

        verify(resumeContextService, never()).getResumeContext(any());
    }

    @Test
    void continueJobChat_unknownEmail_throwsNotFoundWithoutLeakingEmail() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

        JobNotFoundException ex = assertThrows(JobNotFoundException.class, () -> aiService.continueJobChat(
                JobChatAiRequest.builder().jobId(JOB_ID).newPrompt("Q").build(), USER_EMAIL));
        assertEquals("Job not found.", ex.getMessage());
        assertFalse(ex.getMessage().contains(USER_EMAIL));
        verify(chatClient, never()).prompt();
    }

    @Test
    void continueJobChat_blankEmail_throwsNotFound() {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume text");

        assertThrows(JobNotFoundException.class, () -> aiService.continueJobChat(
                JobChatAiRequest.builder().jobId(JOB_ID).newPrompt("Q").build(), "  "));
        verify(userRepository, never()).findByEmail(anyString());
        verify(chatClient, never()).prompt();
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
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("upstream failed"));

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

    private void stubFailingProviderCall(String providerMessage) {
        when(resumeContextService.getResumeContext(null)).thenReturn("resume");
        when(promptTemplateService.buildSystemPrompt(any())).thenReturn("SYSTEM");
        when(promptTemplateService.buildUserMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn("USER");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.Builder.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException(providerMessage));
    }

    private OpenAiChatOptions capturedOptions() {
        ArgumentCaptor<OpenAiChatOptions.Builder> captor = ArgumentCaptor.forClass(OpenAiChatOptions.Builder.class);
        verify(requestSpec).options(captor.capture());
        return captor.getValue().build();
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
