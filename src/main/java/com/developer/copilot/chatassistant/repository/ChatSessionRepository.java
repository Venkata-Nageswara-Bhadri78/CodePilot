package com.developer.copilot.chatassistant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.developer.copilot.chatassistant.entity.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByJobId(Long jobId);

    /**
     * Eagerly fetches the associated job in the same query so rendering a "my chats" list
     * (job title/company + chat title) never triggers one lazy-load query per session.
     */
    @Query("SELECT cs FROM ChatSession cs JOIN FETCH cs.job WHERE cs.user.id = :userId ORDER BY cs.updatedAt DESC")
    List<ChatSession> findAllByUserIdWithJob(@Param("userId") Long userId);
}
