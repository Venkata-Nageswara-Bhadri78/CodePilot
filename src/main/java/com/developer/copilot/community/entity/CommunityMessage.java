package com.developer.copilot.community.entity;

import com.developer.copilot.auth.entity.BaseEntity;
import com.developer.copilot.auth.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One text message in a community. The sender is {@code users.id}, not a copied profile.
 * <p>
 * History pagination orders by {@code created_at} then {@code id}. The index matches that
 * cursor so two messages written in the same timestamp still have a stable order.
 */
@Entity
@Table(
        name = "community_messages",
        indexes = @Index(
                name = "idx_community_message_history",
                columnList = "community_id, created_at, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
