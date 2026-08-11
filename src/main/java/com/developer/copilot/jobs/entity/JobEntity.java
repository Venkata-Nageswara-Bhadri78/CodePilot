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
    @Column(name = "source_url_hash", length = 64)
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
}