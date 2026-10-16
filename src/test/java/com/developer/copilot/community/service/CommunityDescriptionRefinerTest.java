package com.developer.copilot.community.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.AiService;

@ExtendWith(MockitoExtension.class)
class CommunityDescriptionRefinerTest {

    @Mock
    private AiService aiService;

    @Test
    void failureKeepsTheSubmittedDescription() {
        when(aiService.refineCommunityDescription("Original duties."))
                .thenThrow(new AiServiceException("The AI service is temporarily unavailable."));
        CommunityDescriptionRefiner refiner = new CommunityDescriptionRefiner(aiService);

        CommunityDescriptionRefiner.Refinement refinement = refiner.refine("Original duties.", 7L);

        assertFalse(refinement.refined());
        assertEquals("Original duties.", refinement.description());
        verify(aiService).refineCommunityDescription("Original duties.");
    }

    @Test
    void successReturnsTheModelText() {
        when(aiService.refineCommunityDescription("Original duties.")).thenReturn("Clearer duties.");
        CommunityDescriptionRefiner refiner = new CommunityDescriptionRefiner(aiService);

        CommunityDescriptionRefiner.Refinement refinement = refiner.refine("Original duties.", 7L);

        assertTrue(refinement.refined());
        assertEquals("Clearer duties.", refinement.description());
    }
}
