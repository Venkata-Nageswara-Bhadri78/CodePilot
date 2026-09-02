package com.developer.copilot.ai.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.developer.copilot.ai.config.AiProperties;
import com.developer.copilot.ai.dto.request.AiChatRequest;
import com.developer.copilot.ai.dto.request.AiMode;
import com.developer.copilot.ai.dto.request.ChatTurnDto;
import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.request.JobExtractionAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;
import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.exception.AiUnavailableException;
import com.developer.copilot.ai.metrics.AiMetrics;
import com.developer.copilot.ai.resilience.AiChatGuard;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.ai.service.context.PromptTemplateService;
import com.developer.copilot.ai.service.context.ResumeContextService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.repository.JobRepository;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private static final String GENERIC_PROVIDER_ERROR =
            "An unexpected error occurred while communicating with the AI model. Please try again.";

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final ResumeContextService resumeContextService;
    private final AiProperties aiProperties;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final AiChatGuard aiChatGuard;
    private final AiMetrics aiMetrics;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Flux<AiStreamChunk> streamChat(AiChatRequest request, Long userId) {
        log.info("Initiating AI streamChat request for userId={}, mode={}", userId, request.getMode());

        String resumeText = resolveResumeText(request.getCustomResumeText(), request.getResumeId());
        String jobDescription = resolveJobDescription(request, userId);
        detachPersistenceContext();

        String systemPrompt = promptTemplateService.buildSystemPrompt(request.getMode());
        String userMessage = promptTemplateService.buildUserMessage(
                request.getPrompt(),
                resumeText,
                jobDescription,
                request.getMode()
        );

        return aiChatGuard.guardStream(() -> startStream(systemPrompt, userMessage, request));
    }

    private Flux<AiStreamChunk> startStream(String systemPrompt, String userMessage, AiChatRequest request) {
        try {
            OpenAiChatOptions.Builder optionsBuilder = chatOptions(request.getMode(), request.getTemperature());

            Flux<AiStreamChunk> contentChunks = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .options(optionsBuilder)
                    .stream()
                    .content()
                    .filter(content -> content != null && !content.isEmpty())
                    .map(content -> AiStreamChunk.builder()
                            .content(content)
                            .isCompleted(false)
                            .model(aiProperties.getDefaultModel())
                            .timestamp(LocalDateTime.now())
                            .build());

            AiStreamChunk completionChunk = AiStreamChunk.builder()
                    .content("")
                    .isCompleted(true)
                    .finishReason("STOP")
                    .model(aiProperties.getDefaultModel())
                    .timestamp(LocalDateTime.now())
                    .build();

            return contentChunks
                    .concatWith(Flux.just(completionChunk))
                    .timeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                    .doOnNext(this::recordStreamOutcome)
                    .onErrorResume(throwable -> {
                        log.error("AI Streaming error: {}", throwable.getMessage(), throwable);
                        recordStreamProviderFailure(throwable);
                        return Flux.just(buildErrorChunk(formatFriendlyErrorMessage(throwable)));
                    });
        } catch (Exception ex) {
            if (shouldPropagate(ex)) {
                throw ex instanceof RuntimeException runtime ? runtime
                        : new AiServiceException(formatFriendlyErrorMessage(ex), ex);
            }
            log.error("Failed to initialize AI stream: {}", ex.getMessage(), ex);
            aiMetrics.recordProviderFailure();
            return Flux.just(buildErrorChunk(formatFriendlyErrorMessage(ex)));
        }
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, Long userId) {
        log.info("Executing synchronous AI chat request for userId={}, mode={}", userId, request.getMode());
        long started = System.nanoTime();

        try {
            String resumeText = resolveResumeText(request.getCustomResumeText(), request.getResumeId());
            String jobDescription = resolveJobDescription(request, userId);
            detachPersistenceContext();

            String systemPrompt = promptTemplateService.buildSystemPrompt(request.getMode());
            String userMessage = promptTemplateService.buildUserMessage(
                    request.getPrompt(),
                    resumeText,
                    jobDescription,
                    request.getMode()
            );

            OpenAiChatOptions.Builder optionsBuilder = chatOptions(request.getMode(), request.getTemperature());

            ChatResponse response = aiChatGuard.call(() -> callWithTimeout(() -> chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .options(optionsBuilder)
                    .call()
                    .chatResponse()));

            AiChatResponse mapped = mapToAiChatResponse(response, request.getMode());
            aiMetrics.recordChatSuccess(Duration.ofNanos(System.nanoTime() - started), mapped.getTotalTokens());
            return mapped;

        } catch (Exception ex) {
            if (shouldPropagate(ex)) {
                throw ex instanceof RuntimeException runtime ? runtime
                        : new AiServiceException(formatFriendlyErrorMessage(ex), ex);
            }
            log.error("AI synchronous generation failed for userId {}: {}", userId, ex.getMessage(), ex);
            aiMetrics.recordProviderFailure();
            throw new AiServiceException(formatFriendlyErrorMessage(ex), ex);
        }
    }

    @Override
    public String getResumeContext() {
        return resumeContextService.getResumeContext(null);
    }

    @Override
    public String getActiveModel() {
        return aiProperties.getDefaultModel() + " (Provider: " + aiProperties.getProvider() + ")";
    }

    @Override
    public JobExtractionAiResponse extractJobInfo(JobExtractionAiRequest request) {
        log.info("Extracting structured job information for URL length={}",
                request.getJobUrl() != null ? request.getJobUrl().length() : 0);

        try {
            String systemPrompt = promptTemplateService.buildJobExtractionSystemPrompt();
            String userMessage = promptTemplateService.buildJobExtractionUserMessage(
                    request.getJobUrl(),
                    request.getRawJobText()
            );

            JobExtractionAiResponse result = callWithTimeout(() -> chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .options(OpenAiChatOptions.builder()
                            .model(aiProperties.getDefaultModel())
                            .maxTokens(aiProperties.getMaxCompletionTokens())
                            .temperature(0.0))
                    .call()
                    .entity(JobExtractionAiResponse.class));

            if (result == null) {
                throw new AiServiceException("AI did not return parsable job information. Please try again.");
            }

            return result;

        } catch (AiServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isDomainException(ex)) {
                throw ex instanceof RuntimeException runtime ? runtime
                        : new AiServiceException(formatFriendlyErrorMessage(ex), ex);
            }
            log.error("AI job extraction failed: {}", ex.getMessage(), ex);
            throw new AiServiceException(formatFriendlyErrorMessage(ex), ex);
        }
    }

    @Override
    public AiChatResponse continueJobChat(JobChatAiRequest request, String userEmail) {
        int inboundTurns = request.getPriorTurns() != null ? request.getPriorTurns().size() : 0;
        log.info("Continuing job chat for userIdLookup={}, jobId={}, inboundTurns={}",
                userEmail != null ? "email" : "none", request.getJobId(), inboundTurns);

        try {
            validatePriorTurns(request.getPriorTurns());
            List<ChatTurnDto> turnsForModel = trimPriorTurns(request.getPriorTurns());

            String resumeText = resolveResumeText(request.getCustomResumeText(), request.getResumeId());
            String jobDescription = resolveJobDescriptionByEmail(request.getJobId(), userEmail, true);
            detachPersistenceContext();

            String systemPrompt = promptTemplateService.buildJobChatSystemPrompt(resumeText, jobDescription);

            List<Message> conversation = new ArrayList<>();
            for (ChatTurnDto turn : turnsForModel) {
                conversation.add(new UserMessage(turn.getUserPrompt()));
                conversation.add(new AssistantMessage(turn.getAiResponse()));
            }
            conversation.add(new UserMessage(request.getNewPrompt()));

            ChatResponse response = aiChatGuard.call(() -> callWithTimeout(() -> chatClient.prompt()
                    .system(systemPrompt)
                    .messages(conversation)
                    .options(chatOptions(AiMode.GENERAL_CHAT, null))
                    .call()
                    .chatResponse()));

            return mapToAiChatResponse(response, AiMode.GENERAL_CHAT);

        } catch (Exception ex) {
            if (shouldPropagate(ex)) {
                throw ex instanceof RuntimeException runtime ? runtime
                        : new AiServiceException(formatFriendlyErrorMessage(ex), ex);
            }
            log.error("AI job chat failed for jobId {}: {}", request.getJobId(), ex.getMessage(), ex);
            aiMetrics.recordProviderFailure();
            throw new AiServiceException(formatFriendlyErrorMessage(ex), ex);
        }
    }

    private OpenAiChatOptions.Builder chatOptions(AiMode mode, Double temperature) {
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(aiProperties.getDefaultModel())
                .maxTokens(aiProperties.maxTokensFor(mode));
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        return optionsBuilder;
    }

    private String formatFriendlyErrorMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return GENERIC_PROVIDER_ERROR;
        }
        String msg = throwable.getMessage();
        if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("quota")
                || msg.contains("Quota exceeded")) {
            return "Google Gemini API rate limit or quota exceeded for the current tier. Please wait a few moments and try again.";
        }
        if (msg.contains("404") || msg.contains("NOT_FOUND")) {
            log.error("AI model was not found. Operators: verify app.ai.default-model.");
            return "The configured AI model is unavailable.";
        }
        if (msg.contains("401") || msg.contains("UNAUTHENTICATED") || msg.contains("invalid authentication")) {
            log.error("AI provider authentication failed. Operators: verify the provider API key.");
            return "AI provider authentication failed. Please try again later.";
        }
        if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
            return "The AI model is currently experiencing high demand. Please try again in a few seconds.";
        }
        if (msg.contains("TimeoutException") || msg.contains("timed out") || msg.contains("Timeout")) {
            aiMetrics.recordTimeout();
            return "The AI service response timed out. Please try a more specific or shorter prompt.";
        }
        return GENERIC_PROVIDER_ERROR;
    }

    private void validatePriorTurns(List<ChatTurnDto> priorTurns) {
        if (priorTurns == null) {
            return;
        }
        if (priorTurns.size() > 40) {
            throw new IllegalArgumentException("Prior turns cannot exceed 40 entries.");
        }
        for (ChatTurnDto turn : priorTurns) {
            if (turn == null
                    || !StringUtils.hasText(turn.getUserPrompt())
                    || !StringUtils.hasText(turn.getAiResponse())) {
                throw new IllegalArgumentException(
                        "Each prior turn must include non-blank userPrompt and aiResponse.");
            }
        }
    }

    private List<ChatTurnDto> trimPriorTurns(List<ChatTurnDto> priorTurns) {
        if (priorTurns == null || priorTurns.isEmpty()) {
            return List.of();
        }
        int keep = Math.max(1, aiProperties.getMaxPriorTurnsSent());
        if (priorTurns.size() <= keep) {
            return priorTurns;
        }
        return new ArrayList<>(priorTurns.subList(priorTurns.size() - keep, priorTurns.size()));
    }

    private AiStreamChunk buildErrorChunk(String friendlyError) {
        return AiStreamChunk.builder()
                .content("\n\nAI Service Error: " + friendlyError)
                .isCompleted(true)
                .finishReason("ERROR")
                .model(aiProperties.getDefaultModel())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void recordStreamOutcome(AiStreamChunk chunk) {
        if (chunk == null || !chunk.isCompleted()) {
            return;
        }
        if ("ERROR".equalsIgnoreCase(chunk.getFinishReason())) {
            aiMetrics.recordStreamError();
        } else {
            aiMetrics.recordStreamStop();
        }
    }

    private void recordStreamProviderFailure(Throwable throwable) {
        aiMetrics.recordProviderFailure();
        if (throwable != null && throwable.getMessage() != null
                && (throwable.getMessage().contains("Timeout") || throwable.getMessage().contains("timed out"))) {
            aiMetrics.recordTimeout();
        }
    }

    private <T> T callWithTimeout(Callable<T> callable) {
        try {
            return Mono.fromCallable(callable)
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                    .block();
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (isDomainException(cause) && cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            log.error("AI provider call failed or timed out: {}", cause.getMessage(), cause);
            throw new AiServiceException(formatFriendlyErrorMessage(cause), cause);
        }
    }

    private boolean shouldPropagate(Throwable throwable) {
        return isDomainException(throwable)
                || throwable instanceof AiUnavailableException
                || throwable instanceof AiServiceException;
    }

    private boolean isDomainException(Throwable throwable) {
        return throwable instanceof JobNotFoundException
                || throwable instanceof ResumeNotFoundException
                || throwable instanceof UserProfileNotFoundException
                || throwable instanceof ResumeParsingException
                || throwable instanceof AiResumePendingException
                || throwable instanceof IllegalArgumentException;
    }

    private AiChatResponse mapToAiChatResponse(ChatResponse response, AiMode mode) {
        String generatedContent = "";
        String finishReason = "STOP";

        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            generatedContent = response.getResult().getOutput().getText();
            if (response.getResult().getMetadata() != null
                    && response.getResult().getMetadata().getFinishReason() != null) {
                finishReason = response.getResult().getMetadata().getFinishReason();
            }
        }

        Long promptTokens = null;
        Long completionTokens = null;
        Long totalTokens = null;

        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            Usage usage = response.getMetadata().getUsage();
            promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null;
            completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null;
            totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : null;
        }

        return AiChatResponse.builder()
                .content(generatedContent)
                .model(aiProperties.getDefaultModel())
                .finishReason(finishReason)
                .mode(mode)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private String resolveResumeText(String customResumeText, Long resumeId) {
        if (customResumeText != null && !customResumeText.isBlank()) {
            return customResumeText.trim();
        }
        try {
            return resumeContextService.getResumeContext(resumeId);
        } catch (ResumeNotFoundException | UserProfileNotFoundException ex) {
            if (resumeId != null) {
                throw ex;
            }
            aiMetrics.recordMissingResume();
            return "";
        }
    }

    private String resolveJobDescription(AiChatRequest request, Long userId) {
        if (request.getJobDescription() != null && !request.getJobDescription().isBlank()) {
            return request.getJobDescription().trim();
        }
        if (request.getJobId() == null) {
            return "";
        }
        return resolveJobDescriptionByUserId(request.getJobId(), userId, true);
    }

    private String resolveJobDescriptionByUserId(Long jobId, Long userId, boolean required) {
        if (jobId == null || userId == null) {
            if (required) {
                throw new JobNotFoundException("Job not found.");
            }
            return "";
        }
        Optional<JobEntity> jobOptional = jobRepository.findByIdAndUserId(jobId, userId);
        if (jobOptional.isEmpty()) {
            throw new JobNotFoundException("Job not found.");
        }
        return jobText(jobOptional.get());
    }

    private String resolveJobDescriptionByEmail(Long jobId, String userEmail, boolean required) {
        if (jobId == null) {
            if (required) {
                throw new JobNotFoundException("Job not found.");
            }
            return "";
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new JobNotFoundException("Job not found.");
        }
        Optional<User> userOptional = userRepository.findByEmail(userEmail);
        if (userOptional.isEmpty()) {
            throw new JobNotFoundException("Job not found.");
        }
        return resolveJobDescriptionByUserId(jobId, userOptional.get().getId(), required);
    }

    private static String jobText(JobEntity job) {
        if (job.getDescription() != null && !job.getDescription().isBlank()) {
            return job.getDescription();
        }
        if (job.getOriginalDescription() != null && !job.getOriginalDescription().isBlank()) {
            return job.getOriginalDescription();
        }
        return "";
    }

    private void detachPersistenceContext() {
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.clear();
        }
    }
}
