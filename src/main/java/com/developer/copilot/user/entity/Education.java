package com.developer.copilot.user.entity;

import com.developer.copilot.auth.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "education")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Education extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "institution_name", nullable = false, length = 300)
    private String institutionName;

    @Column(name = "field", nullable = false, length = 300)
    private String field;

    @Column(name = "start_year", nullable = false)
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Column(name = "score_or_grade", length = 50)
    private String scoreOrGrade;

}
