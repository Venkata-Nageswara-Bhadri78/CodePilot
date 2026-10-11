package com.developer.copilot.jobs.entity;

import com.developer.copilot.auth.entity.BaseEntity;
import com.developer.copilot.auth.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Partial job captured when an automatic save cannot be stored on {@code jobs}.
 * Content columns are nullable so an incomplete extraction can still be kept.
 * Notes and application status are omitted: those exist only after a real job is saved.
 * The only required association is the owning user.
 */
@Entity
@Table(
        name = "jobs_draft",
        uniqueConstraints = @UniqueConstraint(
                name = JobDraftEntity.UNIQUE_USER_URL_HASH,
                columnNames = {"user_id", "source_url_hash"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDraftEntity extends BaseEntity {

    public static final String UNIQUE_USER_URL_HASH = "uk_job_draft_user_source_url_hash";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_url", length = 2000, nullable = true)
    private String sourceUrl;

    @Column(name = "source_url_hash", length = 64, nullable = true)
    private String sourceUrlHash;

    @Lob
    @Column(name = "original_description", columnDefinition = "TEXT", nullable = true)
    private String originalDescription;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = true)
    private String description;

    @Column(length = 255, nullable = true)
    private String title;

    @Column(length = 255, nullable = true)
    private String company;

    @Column(length = 255, nullable = true)
    private String location;

    @Column(name = "employment_type", length = 100, nullable = true)
    private String employmentType;

    @Column(name = "work_mode", length = 50, nullable = true)
    private String workMode;

    @Column(length = 100, nullable = true)
    private String experience;

    @Column(length = 100, nullable = true)
    private String salary;

    @Column(length = 255, nullable = true)
    private String education;

    @Column(length = 100, nullable = true)
    private String department;

    @Column(length = 100, nullable = true)
    private String industry;

    @Column(name = "source_platform", length = 50, nullable = true)
    private String sourcePlatform;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = true)
    private String skills;

    @Column(name = "resume")
    private Long resume;

    @Column(name = "resume_to_job_score")
    private Integer resumeToJobScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 32)
    private JobDraftFailureCategory failureCategory;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
}
