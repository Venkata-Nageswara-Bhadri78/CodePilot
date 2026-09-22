package com.developer.copilot.jobs.entity;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_user_source_url_hash",
                columnNames = {"user_id", "source_url_hash"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User who saved this job.
     * One User -> Many Jobs
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Canonicalized URL pasted by the user (same job posting from different sources/tracking
     * links always normalizes to the same string - see {@code UrlNormalizationUtil}).
     * Mandatory: every job must be traceable back to its source posting.
     */
    @Column(length = 2000, nullable = false)
    private String sourceUrl;

    /**
     * SHA-256 hex digest of {@link #sourceUrl}, used as a fixed-length uniqueness key
     * (MySQL/InnoDB cannot place a unique index directly on a VARCHAR(2000) column).
     */
    @Column(name = "source_url_hash", length = 64, nullable = false)
    private String sourceUrlHash;

    /**
     * Original text pasted by the user before AI processing. Mandatory: this is the
     * authoritative source-of-truth record of what the user actually submitted.
     */
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String originalDescription;

    /**
     * Cleaned / extracted job description.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Basic Job Information
     */
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    private String location;

    private String employmentType;

    private String workMode;

    private String experience;

    private String salary;

    private String department;

    private String education;

    private String industry;

    private String sourcePlatform;

    /**
     * Required Skills
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "job_skills",
            joinColumns = @JoinColumn(name = "job_id")
    )
    @Column(name = "skill")
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    /**
     * Unique identifier of the resume selected for this job ({@code resumes.id}).
     * Stored as a plain id so deleting a resume does not block job rows.
     * Null when the user has no usable resume.
     */
    @Column(name = "resume")
    private Long resume;

    /**
     * AI match score of the selected resume against this job. Strictly 0–100.
     */
    @Column(name = "resume_to_job_score")
    @Builder.Default
    private Integer resumeToJobScore = 0;

    /**
     * Free-form user notes. Independent of extraction and scoring. Empty on create.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private String notes = "";

    /**
     * Manual application pipeline status. Defaults to {@link JobStatus#APPLIED}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", length = 32)
    @Builder.Default
    private JobStatus jobStatus = JobStatus.APPLIED;

    /**
     * User-entered label used only when {@link #jobStatus} is {@link JobStatus#CUSTOM}.
     */
    @Column(name = "custom_status", length = 100)
    private String customStatus;

    @PrePersist
    void applyNewFieldDefaults() {
        if (notes == null) {
            notes = "";
        }
        if (resumeToJobScore == null) {
            resumeToJobScore = 0;
        }
        if (jobStatus == null) {
            jobStatus = JobStatus.APPLIED;
        }
    }
}