package com.developer.copilot.community.service;

import org.springframework.stereotype.Component;

import com.developer.copilot.ai.service.AiService;

import lombok.extern.slf4j.Slf4j;

/**
 * Optional description rewrite. A failure returns the caller's text and does not persist anything.
 */
@Slf4j
@Component
public class CommunityDescriptionRefiner {

    private final AiService aiService;

    public CommunityDescriptionRefiner(AiService aiService) {
        this.aiService = aiService;
    }

    public Refinement refine(String source, Long userId) {
        try {
            String refined = aiService.refineCommunityDescription(source);
            if (refined.length() > CommunityLimits.MAX_DESCRIPTION_LENGTH) {
                log.warn("Community description refinement exceeded the length limit for userId={}", userId);
                return Refinement.original(source);
            }
            return new Refinement(refined, true);
        } catch (RuntimeException ex) {
            log.warn("Community description refinement failed for userId={}: {}", userId, ex.getClass().getSimpleName());
            return Refinement.original(source);
        }
    }

    public record Refinement(String description, boolean refined) {

        static Refinement original(String source) {
            return new Refinement(source, false);
        }
    }
}
