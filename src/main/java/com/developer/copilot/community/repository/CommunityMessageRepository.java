package com.developer.copilot.community.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.developer.copilot.community.entity.CommunityMessage;

public interface CommunityMessageRepository extends JpaRepository<CommunityMessage, Long> {

    /**
     * Newest page of a community. Pass a {@link Pageable} that sets the limit and does not add a sort.
     */
    List<CommunityMessage> findByCommunityIdOrderByCreatedAtDescIdDesc(Long communityId, Pageable pageable);

    /**
     * Messages strictly older than the cursor {@code (createdAt, messageId)}, newest first.
     * Pass an unsorted {@link Pageable}; this query already defines the order that matches
     * {@code idx_community_message_history}.
     */
    @Query("""
            SELECT m FROM CommunityMessage m
            WHERE m.community.id = :communityId
              AND (
                    m.createdAt < :createdAt
                    OR (m.createdAt = :createdAt AND m.id < :messageId)
                  )
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<CommunityMessage> findOlderThan(
            @Param("communityId") Long communityId,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("messageId") Long messageId,
            Pageable pageable);
}
