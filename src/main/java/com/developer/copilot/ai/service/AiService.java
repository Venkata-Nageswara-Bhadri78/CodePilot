package com.developer.copilot.ai.service;

import com.developer.copilot.ai.dto.request.AiChatRequest;
import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.request.JobExtractionAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;

import reactor.core.publisher.Flux;

/**
 * Primary AI Service interface for conversational career copiloting, resume review,
 * job description matching, and interview prep.
 */
public interface AiService {

    /**
     * Streams real-time tokens for a user request via reactive Flux (Server-Sent Events).
     *
     * @param request the chat prompt and context
     * @param userEmail the authenticated user's email
     * @return a Flux stream of incremental {@link AiStreamChunk} tokens
     */
    Flux<AiStreamChunk> streamChat(AiChatRequest request, String userEmail);

    /**
     * Executes a synchronous AI completion call.
     *
     * @param request the chat prompt and context
     * @param userEmail the authenticated user's email
     * @return the complete {@link AiChatResponse}
     */
    AiChatResponse chat(AiChatRequest request, String userEmail);

    /**
     * Retrieves the current candidate resume context.
     *
     * @param userEmail the authenticated user's email
     * @return formatted resume text
     */
    String getResumeContext(String userEmail);

    /**
     * Returns the active model identifier and provider info.
     *
     * @return model identifier string
     */
    String getActiveModel();

    /**
     * Parses a pasted job posting into strict structured JSON with zero hallucination -
     * any field not clearly present in the source text is returned empty rather than guessed.
     * Used exclusively by the {@code jobextraction} module's "Extract Job Info" flow.
     *
     * @param request the canonicalized job URL and raw pasted posting text
     * @return the strictly parsed job fields
     */
    JobExtractionAiResponse extractJobInfo(JobExtractionAiRequest request);

    /**
     * Continues a multi-turn, job-grounded chat conversation: rebuilds the conversation from
     * the supplied prior turns and answers the new prompt in that context. Stateless from the
     * AI module's point of view - all persistence/history-loading is the caller's
     * ({@code chatassistant} module's) responsibility.
     *
     * @param request the job to ground the conversation in, prior turns, and the new prompt
     * @param userEmail the authenticated user's email
     * @return the AI's response to the new prompt
     */
    AiChatResponse continueJobChat(JobChatAiRequest request, String userEmail);
}
