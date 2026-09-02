package com.developer.copilot.user.dto.parsing;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Parsed information for a single resume.
 * <p>
 * This is the internal service-to-service contract for resume context. It is
 * consumed in-process by other modules and over HTTP by the internal resume
 * endpoint; it is not part of the public user-facing API.
 */
@Getter
@Builder
public class ResumeParsedDataResponse {

    private Long resumeId;

    private String originalFilename;

    private Boolean highPriority;

    /**
     * One of {@code PENDING}, {@code COMPLETED} or {@code FAILED}. Callers should
     * treat anything other than {@code COMPLETED} as "no usable resume context"
     * and fall back accordingly.
     */
    private String status;

    private Integer attemptCount;

    private String lastError;

    private String parserVersion;

    private LocalDateTime parsedAt;

    private Integer pageCount;

    private Integer characterCount;

    /**
     * True when extracted text was cut at the configured maximum length.
     */
    private Boolean truncated;

    private String candidateName;

    private String email;

    private String phone;

    private String location;

    private String linkedinUrl;

    private String githubUrl;

    /**
     * Detected sections keyed by canonical section name.
     */
    private Map<String, String> sections;

    private String rawText;

    /**
     * The parsed resume rendered as structured plain text, ready to be dropped
     * straight into an AI prompt. Null when parsing has not completed.
     */
    private String contextText;
}
