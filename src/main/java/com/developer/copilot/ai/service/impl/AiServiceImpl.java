package com.developer.copilot.ai.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

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
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.ai.service.context.PromptTemplateService;
import com.developer.copilot.ai.service.context.ResumeContextService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Production implementation of {@link AiService} utilizing Spring AI {@link ChatClient}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final ResumeContextService resumeContextService;
    private final AiProperties aiProperties;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    @Override
    public Flux<AiStreamChunk> streamChat(AiChatRequest request, String userEmail) {
        log.info("Initiating AI streamChat request for user: {}, mode: {}", userEmail, request.getMode());

        try {
            String resumeText = resolveResumeText(request, userEmail);
            String jobDescription = resolveJobDescription(request, userEmail);

            String systemPrompt = promptTemplateService.buildSystemPrompt(request.getMode());
            String userMessage = promptTemplateService.buildUserMessage(
                    request.getPrompt(),
                    resumeText,
                    jobDescription,
                    request.getMode()
            );

            log.debug("Calling Spring AI ChatClient streaming...");

            Flux<AiStreamChunk> contentChunks = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
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
                    .timeout(Duration.ofSeconds(aiProperties.getStreamingTimeoutSeconds()))
                    .onErrorResume(throwable -> {
                        log.error("AI Streaming error for user {}: {}", userEmail, throwable.getMessage(), throwable);
                        String friendlyError = formatFriendlyErrorMessage(throwable);
                        AiStreamChunk errorChunk = AiStreamChunk.builder()
                                .content("\n\n⚠️ **AI Service Error:** " + friendlyError)
                                .isCompleted(true)
                                .finishReason("ERROR")
                                .model(aiProperties.getDefaultModel())
                                .timestamp(LocalDateTime.now())
                                .build();
                        return Flux.just(errorChunk);
                    });

        } catch (Exception ex) {
            log.error("Failed to initialize AI stream for user {}: {}", userEmail, ex.getMessage(), ex);
            String friendlyError = formatFriendlyErrorMessage(ex);
            AiStreamChunk errorChunk = AiStreamChunk.builder()
                    .content("⚠️ **Failed to initialize AI Chat Stream:** " + friendlyError)
                    .isCompleted(true)
                    .finishReason("ERROR")
                    .model(aiProperties.getDefaultModel())
                    .timestamp(LocalDateTime.now())
                    .build();
            return Flux.just(errorChunk);
        }
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, String userEmail) {
        log.info("Executing synchronous AI chat request for user: {}, mode: {}", userEmail, request.getMode());

        try {
            String resumeText = resolveResumeText(request, userEmail);
            String jobDescription = resolveJobDescription(request, userEmail);

            String systemPrompt = promptTemplateService.buildSystemPrompt(request.getMode());
            String userMessage = promptTemplateService.buildUserMessage(
                    request.getPrompt(),
                    resumeText,
                    jobDescription,
                    request.getMode()
            );

            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .chatResponse();

            return mapToAiChatResponse(response, request.getMode());

        } catch (Exception ex) {
            log.error("AI synchronous generation failed for user {}: {}", userEmail, ex.getMessage(), ex);
            throw new AiServiceException(formatFriendlyErrorMessage(ex), ex);
        }
    }

    @Override
    public String getResumeContext(String userEmail) {
        return resumeContextService.getResumeContext(null);
    }

    /**
     * Translates technical upstream LLM exceptions into clean, actionable messages for the frontend.
     */
    private String formatFriendlyErrorMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "An unexpected error occurred while communicating with the AI model.";
        }
        String msg = throwable.getMessage();
        if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("quota") || msg.contains("Quota exceeded")) {
            return "Google Gemini API rate limit or quota exceeded for the current tier. Please wait a few moments and try again.";
        }
        if (msg.contains("404") || msg.contains("NOT_FOUND")) {
            return "The requested AI model was not found or is deprecated. Please verify 'app.ai.default-model' in application.properties.";
        }
        if (msg.contains("401") || msg.contains("UNAUTHENTICATED") || msg.contains("invalid authentication")) {
            return "AI provider authentication failed. Please verify your GEMINI_API_KEY in application.properties.";
        }
        if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
            return "The AI model is currently experiencing high demand. Please try again in a few seconds.";
        }
        if (msg.contains("TimeoutException") || msg.contains("timed out")) {
            return "The AI service response timed out. Please try a more specific or shorter prompt.";
        }
        return msg;
    }

    @Override
    public String getActiveModel() {
        return aiProperties.getDefaultModel() + " (Provider: " + aiProperties.getProvider() + ")";
    }

    @Override
    public JobExtractionAiResponse extractJobInfo(JobExtractionAiRequest request) {
        log.info("Extracting structured job information for URL: {}", request.getJobUrl());

        try {
            String systemPrompt = promptTemplateService.buildJobExtractionSystemPrompt();
            String userMessage = promptTemplateService.buildJobExtractionUserMessage(
                    request.getJobUrl(),
                    request.getRawJobText()
            );

            JobExtractionAiResponse result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    // Deterministic extraction: no creativity/variance allowed for structured parsing.
                    .options(OpenAiChatOptions.builder().temperature(0.0))
                    .call()
                    .entity(JobExtractionAiResponse.class);

            if (result == null) {
                throw new AiServiceException("AI did not return parsable job information. Please try again.");
            }

            return result;

        } catch (AiServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("AI job extraction failed for URL {}: {}", request.getJobUrl(), ex.getMessage(), ex);
            throw new AiServiceException(formatFriendlyErrorMessage(ex), ex);
        }
    }

    @Override
    public AiChatResponse continueJobChat(JobChatAiRequest request, String userEmail) {
        int turnNumber = (request.getPriorTurns() != null ? request.getPriorTurns().size() : 0) + 1;
        log.info("Continuing job chat for user: {}, jobId: {}, turn: {}", userEmail, request.getJobId(), turnNumber);

        try {
            String resumeText = resolveResumeText(request.getCustomResumeText(), request.getResumeId());
            String jobDescription = resolveJobDescriptionByJobId(request.getJobId(), userEmail);

            String systemPrompt = promptTemplateService.buildJobChatSystemPrompt(resumeText, jobDescription);

            List<Message> conversation = new ArrayList<>();
            if (request.getPriorTurns() != null) {
                for (ChatTurnDto turn : request.getPriorTurns()) {
                    conversation.add(new UserMessage(turn.getUserPrompt()));
                    conversation.add(new AssistantMessage(turn.getAiResponse()));
                }
            }
            conversation.add(new UserMessage(request.getNewPrompt()));

            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(conversation)
                    .call()
                    .chatResponse();

            return mapToAiChatResponse(response, null);

        } catch (Exception ex) {
            log.error("AI job chat failed for user {} jobId {}: {}", userEmail, request.getJobId(), ex.getMessage(), ex);
            throw new AiServiceException(formatFriendlyErrorMessage(ex), ex);
        }
    }

    /**
     * Maps a raw Spring AI {@link ChatResponse} into the project's {@link AiChatResponse}
     * contract - shared between {@link #chat} and {@link #continueJobChat} so the token/finish
     * reason extraction logic exists in exactly one place.
     */
    private AiChatResponse mapToAiChatResponse(ChatResponse response, AiMode mode) {
        String generatedContent = "";
        String finishReason = "STOP";

        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            generatedContent = response.getResult().getOutput().getText();
            if (response.getResult().getMetadata() != null && response.getResult().getMetadata().getFinishReason() != null) {
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

    /**
     * Resolves candidate resume context from custom request override or default resume service.
     */
    private String resolveResumeText(AiChatRequest request, String userEmail) {
        return resolveResumeText(request.getCustomResumeText(), request.getResumeId());
    }

    private String resolveResumeText(String customResumeText, Long resumeId) {
        if (customResumeText != null && !customResumeText.isBlank()) {
            return customResumeText.trim();
        }
        return resumeContextService.getResumeContext(resumeId);
    }

    /**
     * Resolves job description from custom text or referenced saved Job entity.
     */
    private String resolveJobDescription(AiChatRequest request, String userEmail) {
        if (request.getJobDescription() != null && !request.getJobDescription().isBlank()) {
            return request.getJobDescription().trim();
        }
        return resolveJobDescriptionByJobId(request.getJobId(), userEmail);
    }

    /**
     * Looks up a saved job owned by the given user and returns its cleaned description,
     * falling back to the original pasted description, or an empty string if unavailable.
     */
    private String resolveJobDescriptionByJobId(Long jobId, String userEmail) {
        if (jobId == null || userEmail == null) {
            return "";
        }

        Optional<User> userOptional = userRepository.findByEmail(userEmail);
        if (userOptional.isEmpty()) {
            return "";
        }

        Optional<JobEntity> jobOptional = jobRepository.findByIdAndUserId(jobId, userOptional.get().getId());
        if (jobOptional.isEmpty()) {
            return "";
        }

        JobEntity job = jobOptional.get();
        if (job.getDescription() != null && !job.getDescription().isBlank()) {
            return job.getDescription();
        }
        if (job.getOriginalDescription() != null && !job.getOriginalDescription().isBlank()) {
            return job.getOriginalDescription();
        }
        return "";
    }
}
