package com.developer.copilot.chatassistant.entity;

import com.developer.copilot.auth.entity.BaseEntity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single conversational turn: one user prompt paired with its AI response. Rows are
 * append-only - a new turn is always a single {@code INSERT}, never a rewrite of prior
 * turns, which is what keeps a growing chat history cheap regardless of its length.
 */
@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_message_session_turn", columnList = "chat_session_id, turn_number")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_chat_message_session_turn", columnNames = {"chat_session_id", "turn_number"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;

    /**
     * 1-based position of this turn within its chat session, used for stable ordering
     * independent of timestamp precision.
     */
    @Column(name = "turn_number", nullable = false)
    private Integer turnNumber;

    @Lob
    @Column(name = "user_prompt", columnDefinition = "TEXT", nullable = false)
    private String userPrompt;

    @Lob
    @Column(name = "ai_response", columnDefinition = "TEXT", nullable = false)
    private String aiResponse;
}
