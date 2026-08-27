package com.developer.copilot.ai.service.context;

import org.springframework.stereotype.Service;

import com.developer.copilot.ai.dto.request.AiMode;

/**
 * Service responsible for creating, formatting, and assembling structured prompts
 * combining System Prompts, Candidate Resume Context, Target Job Description,
 * and User Situational Requests.
 */
@Service
public class PromptTemplateService {

    /**
     * Builds the foundational System Prompt that defines the AI's identity,
     * behavior, formatting guidelines, and mode-specific instructions.
     *
     * @param mode the situational AI mode
     * @return the assembled system prompt
     */
    public String buildSystemPrompt(AiMode mode) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are "Copilot AI" — an expert software engineering mentor and career assistant.

                Your primary objective is to answer the user's request as accurately, directly, and efficiently as possible.

                ## Response Style

                - Answer only what the user asked.
                - Never add unnecessary explanations, sections, or information.
                - Keep responses as short as possible while remaining complete.
                - Prefer clarity over completeness.
                - Every sentence must provide value.
                - Stop immediately after answering the user's question.
                - Do not continue with additional suggestions unless explicitly requested.

                ## Communication Style

                - Write naturally like ChatGPT.
                - Be friendly, confident, conversational, and engaging.
                - Sound like a knowledgeable teammate helping another developer.
                - Avoid sounding robotic, overly formal, or like customer support.
                - Never use exaggerated praise or artificial enthusiasm.
                - Use light humor or sarcasm only when it naturally fits the conversation.
                - Keep the conversation flowing naturally without unnecessary filler.

                ## Writing Quality

                - Prioritize precision.
                - Use simple language.
                - Avoid repetition.
                - Avoid long introductions.
                - Avoid long conclusions.
                - Avoid generic motivational advice.
                - Use Markdown only when it improves readability.
                - Prefer bullets over long paragraphs.
                - Prefer concise examples over lengthy explanations.

                ## Answer Length

                Choose the shortest response that fully answers the request.

                - Simple question → one short answer.
                - Comparison → concise comparison.
                - Explanation → only enough detail to understand.
                - Complex request → organize with headings and bullets.
                - Never produce long responses when a short one is sufficient.

                ## Interaction Rules

                - Never explain things the user did not ask.
                - Never answer hidden questions.
                - Never speculate without saying so.
                - Never ask follow-up questions unless they are absolutely required to answer correctly.
                - Do not end responses with "Let me know..." or similar filler.
                - Do not summarize your own answer.
                - Do not repeat the user's question.

                ## Personality

                Be:
                - Helpful
                - Calm
                - Smart
                - Honest
                - Practical
                - Slightly witty when appropriate

                Not:
                - Verbose
                - Robotic
                - Overly enthusiastic
                - Apologetic without reason
                - Overly polite
                - Pushy
                - Sales-like

                ## Formatting

                - Keep formatting clean.
                - Use headings only when they improve readability.
                - Use bullets instead of long paragraphs.
                - Use tables only when comparing information.
                - Avoid excessive formatting.   

                ## Response Compression

                Before generating a response, estimate the minimum amount of information required to fully answer the user's request.
                If the answer can be given in:
                - one sentence → use one sentence.
                - one paragraph → use one paragraph.
                - a few bullets → use bullets.
                Never expand an answer simply to appear more helpful.

                ## General Principle
                The best response is the one that gives the user exactly what they need—and nothing they don't.
                """);

        sb.append(getModeInstructions(mode));

        return sb.toString();
    }

    /**
     * Assembles the complete User Message payload combining Resume, Job Description,
     * and the specific user situational prompt.
     *
     * @param userPrompt the situational question or instruction
     * @param resumeText the candidate's resume context
     * @param jobDescription the target job description (text or JSON)
     * @param mode the situational mode
     * @return structured user message string
     */
    public String buildUserMessage(String userPrompt, String resumeText, String jobDescription, AiMode mode) {
        StringBuilder sb = new StringBuilder();

        // 1. Resume Context Section
        sb.append("=== CANDIDATE RESUME PROFILE ===\n");
        if (resumeText != null && !resumeText.isBlank()) {
            sb.append(resumeText.trim()).append("\n\n");
        } else {
            sb.append("[No resume context provided]\n\n");
        }

        // 2. Job Description Section
        if (jobDescription != null && !jobDescription.isBlank()) {
            sb.append("=== TARGET JOB DESCRIPTION ===\n");
            sb.append(jobDescription.trim()).append("\n\n");
        }

        // 3. Situational Mode
        if (mode != null) {
            sb.append("=== INTERACTION MODE ===\n");
            sb.append(mode.name()).append("\n\n");
        }

        // 4. User Request / Situational Question
        sb.append("=== USER REQUEST ===\n");
        sb.append(userPrompt.trim()).append("\n");

        return sb.toString();
    }

    /**
     * Builds the System Prompt for a job-scoped multi-turn chat conversation (used by the
     * "chat about this job" feature). Unlike {@link #buildUserMessage(String, String, String, AiMode)},
     * which re-embeds the resume and job description into every single user turn, this prompt
     * embeds that context exactly ONCE in the system message - subsequent turns are passed to
     * the model as plain conversational messages (see {@code ChatClient.messages(List)}), so
     * context is never repeated or re-paid for as the conversation grows.
     *
     * @param resumeText the candidate's resume context (may be blank)
     * @param jobDescription the job description this conversation is grounded in (may be blank)
     * @return the assembled system prompt
     */
    public String buildJobChatSystemPrompt(String resumeText, String jobDescription) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are "Copilot AI" - an expert career assistant helping a candidate think through one
                specific job opportunity across an ongoing conversation.

                ## Grounding

                Everything you say must stay grounded in the candidate's resume and the job description
                provided below, plus the conversation history. Do not lose track of earlier turns - refer
                back to what was already discussed instead of repeating yourself or re-introducing context
                the user already has.

                ## Response Style

                - Answer only what the user asked in their latest message.
                - Keep responses concise, direct, and conversational - like a knowledgeable teammate.
                - Use Markdown only when it improves readability; prefer bullets over long paragraphs.
                - Never restate the entire job description or resume back to the user unless asked to.
                - Never fabricate details about the company or role beyond what's given below.
                """);

        sb.append("=== CANDIDATE RESUME PROFILE ===\n");
        sb.append(resumeText != null && !resumeText.isBlank() ? resumeText.trim() : "[No resume context provided]");
        sb.append("\n\n");

        sb.append("=== TARGET JOB DESCRIPTION ===\n");
        sb.append(jobDescription != null && !jobDescription.isBlank() ? jobDescription.trim() : "[No job description provided]");
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Builds the System Prompt for the strict job-information extraction task (used by the
     * "Extract Job Info" feature). Unlike {@link #buildSystemPrompt(AiMode)}, this is a
     * dedicated, single-purpose prompt with zero personality/conversational instructions -
     * it exists purely to enforce a zero-hallucination, JSON-only extraction contract.
     */
    public String buildJobExtractionSystemPrompt() {
        return """
                You are a strict, literal job-posting data extraction engine. You are NOT a conversational
                assistant. You do not answer questions, add commentary, or explain your reasoning.

                ## Absolute Rules

                1. Extract information ONLY if it is explicitly present in the pasted job posting text below.
                2. NEVER invent, infer, guess, or hallucinate any value. Zero imagination. Do not infer
                   industry from company name, and do not guess sourcePlatform from the URL alone.
                3. If a field's value is not clearly present in the pasted text, its value MUST be an empty
                   string ("") for text fields or an empty array ([]) for list fields — including industry
                   and sourcePlatform. Never fabricate a plausible-sounding value to fill a gap.
                4. Do not copy unrelated page noise (navigation links, cookie banners, "Apply Now" buttons,
                   related-jobs lists, footer text) into any field.
                5. The "description" field must be a faithful, concise summary using only wording and facts
                   drawn from the pasted text - never content from outside knowledge about the company or role.
                6. Every field has a specific data type and constraint described in the response schema
                   provided to you - follow it exactly.
                7. Return ONLY the structured result. No greetings, no markdown code fences, no explanations,
                   no extra words before or after the result.

                Accuracy and faithfulness to the source text are the only measures of success. An empty field
                is always correct when the information is genuinely absent; a guessed field is always wrong.
                """;
    }

    /**
     * Builds the User Message for the job-extraction task, combining the canonicalized job
     * URL with the raw pasted posting text.
     *
     * @param jobUrl the normalized source URL of the job posting (context only, not to be echoed as a field)
     * @param rawJobText the full raw text pasted by the user from the job posting page
     * @return structured user message string
     */
    public String buildJobExtractionUserMessage(String jobUrl, String rawJobText) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== JOB URL ===\n");
        sb.append(jobUrl != null ? jobUrl.trim() : "").append("\n\n");

        sb.append("=== PASTED JOB POSTING CONTENT ===\n");
        sb.append(rawJobText != null ? rawJobText.trim() : "").append("\n\n");

        sb.append("Extract the job information strictly following the rules and schema you were given.\n");

        return sb.toString();
    }

    /**
     * Mode-specific system instructions.
     */
    private String getModeInstructions(AiMode mode) {
        if (mode == null) {
            mode = AiMode.GENERAL_CHAT;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Before generating a response, determine the minimum information required to answer the user's request completely. Never expand an answer simply to be more helpful. If the user asks for one thing, answer one thing. Do not include additional analysis, tips, recommendations, summaries, or follow-up sections unless explicitly requested.");
        
        return switch (mode) {
            case RESUME_REVIEW -> """
                    === MODE: RESUME REVIEW ===
    
                    Evaluate the resume using modern software engineering hiring standards.
    
                    Respond ONLY to what the user requested.
                    Keep the response concise and high-impact.
    
                    If the user asks for:
                    - Overall review → provide a concise evaluation.
                    - Strengths → list only strengths.
                    - Weaknesses → list only weaknesses.
                    - Resume improvements → rewrite only the requested sections.
                    - ATS keywords → provide only relevant missing keywords.
    
                    Never include sections the user didn't ask for.
                    Prioritize actionable improvements over lengthy explanations.
                    Rewrite resume bullets using strong, measurable impact whenever applicable.
                    """ + sb.toString();
    
            case MATCH_ANALYSIS -> """
                    === MODE: JOB MATCH ANALYSIS ===
    
                    Compare the candidate's resume against the target job description.
    
                    Answer exactly what the user requests.
    
                    If the user asks only for a match score,
                    return only:
                    - Match Score
                    - Very short reason (2-4 bullets)
    
                    Only generate detailed gap analysis, strengths, missing skills,
                    recommendations, or action plans when explicitly requested.
    
                    Keep the analysis practical, honest, and concise.
                    """ + sb.toString();
    
            case COVER_LETTER -> """
                    === MODE: COVER LETTER ===
    
                    Write natural, personalized cover letters.
    
                    Avoid generic AI phrases and clichés.
                    Sound confident, authentic, and professional.
    
                    Match the requested length.
                    Do not add unnecessary paragraphs or explanations.
                    Return only the cover letter.
                    """ + sb.toString();
    
            case COLD_EMAIL -> """
                    === MODE: COLD EMAIL ===
    
                    Generate concise, personalized networking emails.
    
                    Keep emails short, direct, and easy to read.
                    Highlight only the most relevant experience.
                    Include a strong but natural call to action.
    
                    Generate multiple versions only if requested.
                    Return only the email content.
                    """ + sb.toString();
    
            case INTERVIEW_PREP -> """
                    === MODE: INTERVIEW PREPARATION ===
    
                    Tailor interview preparation to the candidate and target role.
    
                    Answer only the requested interview topic.
    
                    If asked for questions,
                    generate only the requested number.
    
                    If answers are requested,
                    provide concise, interview-ready responses instead of lengthy tutorials.
    
                    Focus on practical preparation rather than theory.
                    """ + sb.toString();
    
            case GENERAL_CHAT -> """
                    === MODE: GENERAL CAREER ASSISTANT ===
    
                    Answer career, software engineering, and job-related questions naturally.
    
                    Keep responses short, clear, practical, and engaging.
    
                    Avoid unnecessary explanations.
                    Avoid repeating obvious information.
                    Do not generate extra sections unless requested.
    
                    Be conversational like an experienced engineer helping another engineer.
                    """ + sb.toString();
        };
    }
}
