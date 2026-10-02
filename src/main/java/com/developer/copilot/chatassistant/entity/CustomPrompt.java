package com.developer.copilot.chatassistant.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
 * A reusable prompt owned by one {@link User}. One user can store many custom prompts;
 * prompts are not shared and have no other entity relationships.
 */
@Entity
@Table(
        name = "custom_prompts",
        indexes = {
                @Index(name = "idx_custom_prompt_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomPrompt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Short label for a sidebar list. Not unique — duplicate titles are allowed.
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * The reusable prompt body the user wants to paste later.
     */
    @Lob
    @Column(name = "prompt", columnDefinition = "TEXT", nullable = false)
    private String prompt;
}
