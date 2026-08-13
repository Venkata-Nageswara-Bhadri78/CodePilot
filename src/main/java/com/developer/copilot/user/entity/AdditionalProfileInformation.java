package com.developer.copilot.user.entity;

import com.developer.copilot.auth.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "additional_profile_information")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdditionalProfileInformation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "link", length = 500)
    private String link;

}
