package com.developer.copilot.ai.service;

import com.developer.copilot.ai.dto.request.AiChatRequest;
import com.developer.copilot.ai.dto.request.JobChatAiRequest;
import com.developer.copilot.ai.dto.request.JobExtractionAiRequest;
import com.developer.copilot.ai.dto.request.ResumeToJobScoreAiRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;

import reactor.core.publisher.Flux;

/**
 * Primary AI Service interface for conversational career copiloting, resume review,
 * job description matching, and interview prep.
 */
public interface AiService {

    Flux<AiStreamChunk> streamChat(AiChatRequest request, Long userId);

    AiChatResponse chat(AiChatRequest request, Long userId);

    String getResumeContext();

    String getActiveModel();

    JobExtractionAiResponse extractJobInfo(JobExtractionAiRequest request);

    /**
     * Score-only AI call used when the user switches the resume bound to a saved job.
     * Returns an integer 0–100 (never a structured job payload).
     */
    Integer scoreResumeToJob(ResumeToJobScoreAiRequest request);

    /**
     * Job-scoped multi-turn chat. {@code userEmail} remains the in-process contract used by
     * chat-assistant; HTTP chat/stream pass {@code userId} instead.
     */
    AiChatResponse continueJobChat(JobChatAiRequest request, String userEmail);
}
