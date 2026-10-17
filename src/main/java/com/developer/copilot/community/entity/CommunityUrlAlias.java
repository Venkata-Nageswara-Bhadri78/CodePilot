package com.developer.copilot.community.entity;

import com.developer.copilot.auth.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One recognized posting URL for a community. Many aliases belong to one community,
 * and one normalized alias belongs to only one community.
 * <p>
 * {@code normalizedUrlHash} is the uniqueness key. InnoDB cannot place a unique index
 * on the VARCHAR(2000) URL (utf8mb4 key-length limit), same reason {@code jobs.source_url_hash}
 * exists. The hash is the SHA-256 hex of {@code normalizedUrl}.
 */
@Entity
@Table(
        name = "community_url_aliases",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_community_url_alias_hash",
                columnNames = "normalized_url_hash"),
        indexes = @Index(name = "idx_community_url_alias_community", columnList = "community_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityUrlAlias extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /** URL as submitted, before normalization. A posting link, not a copy of a job row. */
    @Column(name = "original_url", length = 2000)
    private String originalUrl;

    @Column(name = "normalized_url", nullable = false, length = 2000)
    private String normalizedUrl;

    @Column(name = "normalized_url_hash", nullable = false, updatable = false, length = 64)
    private String normalizedUrlHash;
}
