package com.developer.copilot.chatassistant.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.developer.copilot.auth.entity.BaseEntity;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.jobs.entity.JobEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single, ongoing conversation about exactly one {@link JobEntity}. Created lazily the
 * first time a user sends a message about a job; the unique constraint on {@code job_id}
 * enforces "one chat per job" directly at the database level.
 * <p>
 * Deliberately holds no messages itself - {@link ChatMessage} rows are appended
 * independently so growing a conversation is always an O(1) insert, never a rewrite of
 * this row.
 */
@Entity
@Table(
        name = "chat_sessions",
        indexes = {
                @Index(name = "idx_chat_session_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The job this chat is about. One job has at most one chat (enforced by the unique
     * constraint below). {@code ON DELETE CASCADE} so deleting the job also removes the session.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private JobEntity job;

    /**
     * Denormalized from {@code job.user} so listing "my chats" never has to join through
     * {@code jobs} - a job's owner never changes, so this never needs to be kept in sync
     * beyond creation time.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Human-readable label, e.g. "Amazon - SDE 1". Deliberately has NO uniqueness
     * constraint - collisions between chats are expected and harmless.
     */
    @Column(name = "chat_title", nullable = false, length = 255)
    private String chatTitle;
}
