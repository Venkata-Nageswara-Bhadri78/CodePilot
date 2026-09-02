package com.developer.copilot.user.entity;

import com.developer.copilot.auth.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Parsed representation of an uploaded resume PDF.
 * <p>
 * Persisted once per resume so the original file is never parsed twice for the
 * same content.
 */
@Entity
@Table(name = "resume_parsed_data")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParsedData extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Source resume. Unique so a resume can never accumulate multiple parsed records.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false, unique = true)
    private Resume resume;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ResumeParsingStatus status = ResumeParsingStatus.PENDING;

    /**
     * Attempts consumed so far. Once it reaches the configured maximum the status
     * becomes {@link ResumeParsingStatus#FAILED} and no further retry is performed.
     */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    /**
     * Version of the extraction logic that produced this record.
     */
    @Column(name = "parser_version", length = 32)
    private String parserVersion;

    @Column(name = "parsed_at")
    private LocalDateTime parsedAt;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "character_count")
    private Integer characterCount;

    /**
     * True when extracted text was cut at {@code resume.parsing.max-text-length}.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean truncated = false;

    /**
     * Normalized full text of the resume.
     */
    @Lob
    @Column(name = "raw_text", columnDefinition = "LONGTEXT")
    private String rawText;

    /**
     * Detected sections as a JSON object keyed by section name.
     */
    @Lob
    @Column(name = "sections_json", columnDefinition = "LONGTEXT")
    private String sectionsJson;

    @Column(name = "candidate_name")
    private String candidateName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "location")
    private String location;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "github_url")
    private String githubUrl;

    public boolean isCompleted() {
        return status == ResumeParsingStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == ResumeParsingStatus.FAILED;
    }

    public boolean isPending() {
        return status == ResumeParsingStatus.PENDING;
    }
}
