package com.developer.copilot.user.entity;

import com.developer.copilot.auth.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "resumes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resume extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owner Profile
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    /**
     * Original uploaded filename.
     */
    @Column(nullable = false)
    private String originalFilename;

    /**
     * Unique file identifier in the storage provider.
     */
    @Column(nullable = false, unique = true)
    private String storageKey;

    /**
     * SHA-256 checksum.
     */
    @Column(nullable = false, length = 64)
    private String checksum;

    /**
     * File size in bytes.
     */
    @Column(nullable = false)
    private Long fileSize;

    /**
     * Content type.
     */
    @Column(nullable = false)
    private String contentType;

    /**
     * User selected primary resume.
     */
    @Column(name="is_primary", nullable = false)
    @Builder.Default
    private Boolean highPriority = false;

    /**
     * Indicates whether the resume is active.
     * Reserved for future use (soft delete, archive, versioning).
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}