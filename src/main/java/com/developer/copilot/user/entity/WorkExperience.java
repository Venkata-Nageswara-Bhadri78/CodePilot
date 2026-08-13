package com.developer.copilot.user.entity;

import com.developer.copilot.auth.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "work_experience")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkExperience extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "job_title", nullable = false, length = 200)
    private String jobTitle;

    @Column(name = "start_year", nullable = false)
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

}
