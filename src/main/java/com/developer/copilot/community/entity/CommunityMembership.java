package com.developer.copilot.community.entity;

import java.time.LocalDateTime;

import com.developer.copilot.auth.entity.BaseEntity;
import com.developer.copilot.auth.entity.User;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One membership row per user per community.
 * <p>
 * Leave updates this row ({@link MembershipStatus#LEFT} and {@code leftAt}). It does not
 * delete the row, the community, or messages. Rejoin updates the same row back to
 * {@link MembershipStatus#ACTIVE} and clears {@code leftAt}. The unique pair
 * {@code (community_id, user_id)} is what stops a second active row, including when two
 * joins race, while still allowing rejoin. {@code user} references {@code users.id}
 * (the same identity jobs and messages use). The community account is a separate
 * one-per-user row; membership does not copy profile fields.
 */
@Entity
@Table(
        name = "community_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_community_membership_community_user",
                columnNames = {"community_id", "user_id"}),
        indexes = {
                @Index(
                        name = "idx_community_membership_user_status",
                        columnList = "user_id, membership_status"),
                @Index(
                        name = "idx_community_membership_community_status",
                        columnList = "community_id, membership_status")
        },
        check = @CheckConstraint(
                name = "ck_community_membership_status_left_at",
                constraint = "(membership_status = 'ACTIVE' AND left_at IS NULL) "
                        + "OR (membership_status = 'LEFT' AND left_at IS NOT NULL)"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityMembership extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_status", nullable = false, length = 16)
    @Builder.Default
    private MembershipStatus membershipStatus = MembershipStatus.ACTIVE;

    /** Business join time for the current active period. Audit {@code createdAt} stays the first insert. */
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @PrePersist
    void applyDefaults() {
        if (membershipStatus == null) {
            membershipStatus = MembershipStatus.ACTIVE;
        }
    }
}
