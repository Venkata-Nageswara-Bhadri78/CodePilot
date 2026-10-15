package com.developer.copilot.community.entity;

import com.developer.copilot.auth.entity.BaseEntity;
import com.developer.copilot.auth.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Discussion group for one real-world job posting.
 * <p>
 * {@code canonicalPostingKey} identifies that posting. It is never {@code jobs.id}:
 * two users can save the same posting as different job rows. The key is the SHA-256
 * hex digest of the canonical normalized posting URL, fixed at creation. Recognized
 * URLs live in {@link CommunityUrlAlias}, not on this row. Title, company, location,
 * and description are the shared header only — not a copy of a user's job.
 */
@Entity
@Table(
        name = "communities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_community_canonical_posting_key",
                        columnNames = "canonical_posting_key"),
                @UniqueConstraint(
                        name = "uk_community_name",
                        columnNames = "community_name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Community extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 64-character lowercase SHA-256 hex of the canonical normalized posting URL.
     * Immutable. Not a foreign key to {@code jobs}.
     */
    @Column(name = "canonical_posting_key", nullable = false, updatable = false, length = 64)
    private String canonicalPostingKey;

    /** Generated display name. Unique and immutable after insert. */
    @Column(name = "community_name", nullable = false, updatable = false, length = 255)
    private String communityName;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String company;

    @Column(length = 255)
    private String location;

    /**
     * Shared description shown to members. Refined text when refinement succeeds,
     * otherwise the permitted source description. Not the user's private job notes.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private User createdBy;
}
