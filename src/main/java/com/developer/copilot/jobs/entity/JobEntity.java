package com.developer.copilot.jobs.entity;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "jobs")
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
     * Original URL pasted by the user.
     */
    @Column(length = 2000)
    private String sourceUrl;

    /**
     * Original text pasted by the user before AI processing.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
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
    private String title;

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