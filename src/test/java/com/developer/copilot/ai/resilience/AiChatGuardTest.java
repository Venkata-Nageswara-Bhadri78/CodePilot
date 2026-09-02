package com.developer.copilot.ai.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.exception.AiUnavailableException;

import reactor.core.publisher.Flux;

class AiChatGuardTest {

    @Test
    void threeAiFailuresOpenCircuit() {
        AiChatGuard guard = new AiChatGuard();
        AiServiceException failure = new AiServiceException("down");

        for (int i = 0; i < 3; i++) {
            assertThrows(AiServiceException.class, () -> guard.call(() -> {
                throw failure;
            }));
        }

        AiUnavailableException open = assertThrows(
                AiUnavailableException.class,
                () -> guard.call(() -> "should-not-run"));
        assertEquals("The AI service is temporarily unavailable. Please try again shortly.", open.getMessage());
    }

    @Test
    void successResetsFailureCount() {
        AiChatGuard guard = new AiChatGuard();
        AiServiceException failure = new AiServiceException("down");

        assertThrows(AiServiceException.class, () -> guard.call(() -> {
            throw failure;
        }));
        assertEquals("ok", guard.call(() -> "ok"));
        assertThrows(AiServiceException.class, () -> guard.call(() -> {
            throw failure;
        }));
        assertThrows(AiServiceException.class, () -> guard.call(() -> {
            throw failure;
        }));
        assertEquals("still-open-not-yet", guard.call(() -> "still-open-not-yet"));
    }

    @Test
    void threeStreamErrorsOpenCircuit() {
        AiChatGuard guard = new AiChatGuard();
        AiStreamChunk error = AiStreamChunk.builder()
                .content("AI Service Error")
                .isCompleted(true)
                .finishReason("ERROR")
                .build();

        for (int i = 0; i < 3; i++) {
            guard.guardStream(() -> Flux.just(error)).blockLast();
        }

        AiUnavailableException open = assertThrows(
                AiUnavailableException.class,
                () -> guard.call(() -> "should-not-run"));
        assertEquals("The AI service is temporarily unavailable. Please try again shortly.", open.getMessage());
    }
}
