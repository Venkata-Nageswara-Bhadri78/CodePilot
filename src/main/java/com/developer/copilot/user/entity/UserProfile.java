package com.developer.copilot.user.entity;

import com.developer.copilot.auth.entity.BaseEntity;
import com.developer.copilot.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "headline", length = 300)
    private String headline;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "technical_skills", columnDefinition = "TEXT")
    private String technicalSkills;

}