package com.developer.copilot.ai.service.context;

/**
 * Service interface responsible for providing user resume context to the AI service.
 * <p>
 * Resume ownership, parsing, and persistence are owned by the user module. This
 * abstraction only requests and returns the appropriate parsed resume context.
 */
public interface ResumeContextService {

    /**
     * Retrieves parsed resume context for the authenticated user.
     *
     * @param resumeId the resume to use, or {@code null} to resolve the user's high-priority resume
     * @return structured resume text ready for AI prompt assembly
     */
    String getResumeContext(Long resumeId);
}
