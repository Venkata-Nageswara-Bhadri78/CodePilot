package com.developer.copilot.chatassistant.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.developer.copilot.chatassistant.entity.ChatSession;

import jakarta.persistence.LockModeType;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByJobIdAndUserId(Long jobId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM ChatSession cs WHERE cs.id = :id")
    Optional<ChatSession> findByIdForUpdate(@Param("id") Long id);

    /**
     * Sidebar list: job is fetched in the same query so we never N+1 on title/company.
     */
    @EntityGraph(attributePaths = "job")
    Page<ChatSession> findAllByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
}
