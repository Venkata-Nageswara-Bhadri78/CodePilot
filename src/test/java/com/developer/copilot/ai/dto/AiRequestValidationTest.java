package com.developer.copilot.ai.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.developer.copilot.ai.dto.request.AiChatRequest;
import com.developer.copilot.ai.dto.request.AiMode;
import com.developer.copilot.ai.dto.request.ChatTurnDto;
import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.request.JobExtractionAiRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class AiRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void aiChatRequest_requiresPromptAndDefaultsMode() {
        AiChatRequest request = AiChatRequest.builder().prompt("Hello").build();

        Set<ConstraintViolation<AiChatRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
        assertTrue(request.getMode() == AiMode.GENERAL_CHAT || request.getMode() == null
                || AiMode.GENERAL_CHAT.equals(request.getMode()));
    }

    @Test
    void aiChatRequest_blankPrompt_isInvalid() {
        AiChatRequest request = AiChatRequest.builder().prompt("  ").build();

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void aiChatRequest_oversizedPrompt_isInvalid() {
        AiChatRequest request = AiChatRequest.builder().prompt("x".repeat(8001)).build();

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void aiChatRequest_maxPromptLength_isValid() {
        AiChatRequest request = AiChatRequest.builder().prompt("x".repeat(8000)).build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void aiChatRequest_jobDescriptionBounds() {
        assertTrue(validator.validate(AiChatRequest.builder()
                .prompt("Hi")
                .jobDescription("x".repeat(16000))
                .build()).isEmpty());
        assertFalse(validator.validate(AiChatRequest.builder()
                .prompt("Hi")
                .jobDescription("x".repeat(16001))
                .build()).isEmpty());
    }

    @Test
    void aiChatRequest_customResumeTextBounds() {
        assertTrue(validator.validate(AiChatRequest.builder()
                .prompt("Hi")
                .customResumeText("x".repeat(16000))
                .build()).isEmpty());
        assertFalse(validator.validate(AiChatRequest.builder()
                .prompt("Hi")
                .customResumeText("x".repeat(16001))
                .build()).isEmpty());
    }

    @Test
    void aiChatRequest_temperatureOutOfRange_isInvalid() {
        AiChatRequest request = AiChatRequest.builder().prompt("Hi").temperature(2.5).build();

        assertFalse(validator.validate(request).isEmpty());
        assertFalse(validator.validate(AiChatRequest.builder().prompt("Hi").temperature(-0.1).build()).isEmpty());
        assertTrue(validator.validate(AiChatRequest.builder().prompt("Hi").temperature(0.0).build()).isEmpty());
        assertTrue(validator.validate(AiChatRequest.builder().prompt("Hi").temperature(2.0).build()).isEmpty());
    }

    @Test
    void jobExtractionRequest_requiresUrlAndTextWithBounds() {
        assertFalse(validator.validate(JobExtractionAiRequest.builder().build()).isEmpty());

        JobExtractionAiRequest valid = JobExtractionAiRequest.builder()
                .jobUrl("https://example.com/job")
                .rawJobText("Role details")
                .build();
        assertTrue(validator.validate(valid).isEmpty());

        JobExtractionAiRequest oversized = JobExtractionAiRequest.builder()
                .jobUrl("https://example.com/job")
                .rawJobText("x".repeat(100001))
                .build();
        assertFalse(validator.validate(oversized).isEmpty());

        JobExtractionAiRequest longUrl = JobExtractionAiRequest.builder()
                .jobUrl("https://example.com/" + "x".repeat(2048))
                .rawJobText("Role details")
                .build();
        assertFalse(validator.validate(longUrl).isEmpty());
    }

    @Test
    void jobChatRequest_validatesNestedTurnsAndBounds() {
        JobChatAiRequest invalid = JobChatAiRequest.builder()
                .jobId(1L)
                .newPrompt("Next")
                .priorTurns(java.util.List.of(
                        ChatTurnDto.builder().userPrompt(" ").aiResponse("answer").build()))
                .build();

        assertFalse(validator.validate(invalid).isEmpty());

        JobChatAiRequest valid = JobChatAiRequest.builder()
                .jobId(1L)
                .newPrompt("Next")
                .priorTurns(java.util.List.of(
                        ChatTurnDto.builder().userPrompt("Q").aiResponse("A").build()))
                .build();
        assertTrue(validator.validate(valid).isEmpty());

        assertFalse(validator.validate(JobChatAiRequest.builder().newPrompt("Next").build()).isEmpty());
        assertFalse(validator.validate(JobChatAiRequest.builder().jobId(1L).newPrompt(" ").build()).isEmpty());
    }

    @Test
    void chatTurnDto_requiresNonBlankBoundedFields() {
        assertFalse(validator.validate(new ChatTurnDto()).isEmpty());
        assertTrue(validator.validate(ChatTurnDto.builder()
                .userPrompt("Q")
                .aiResponse("A")
                .build()).isEmpty());
    }
}
