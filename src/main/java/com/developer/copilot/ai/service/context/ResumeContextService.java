package com.developer.copilot.ai.service.context;

/**
 * Service interface responsible for providing user resume context to the AI service.
 * <p>
 * This abstraction enables switching from the current static default profile
 * to dynamic database / user-service resume extraction without altering AI logic.
 */
public interface ResumeContextService {

    /**
     * Retrieves resume context for a specific authenticated user.
     *
     * @param userEmail the email of the authenticated user
     * @return structured resume text
     */
    String getResumeContext(String userEmail);

    /**
     * Returns the default production-ready sample resume context.
     *
     * @return default candidate resume in text format
     */
    String getDefaultResumeContext();
}
