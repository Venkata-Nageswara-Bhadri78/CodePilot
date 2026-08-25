package com.developer.copilot.chatassistant.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.developer.copilot.chatassistant.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findAllByChatSessionIdOrderByTurnNumberAsc(Long chatSessionId);

    Page<ChatMessage> findAllByChatSessionIdOrderByTurnNumberAsc(Long chatSessionId, Pageable pageable);

    int countByChatSessionId(Long chatSessionId);

    /**
     * Single bulk-delete SQL statement, used when a chat is removed - avoids loading every
     * historical turn into the persistence context just to remove it one by one (a plain
     * derived {@code deleteBy...} method would select-then-remove each row individually).
     */
    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.chatSession.id = :chatSessionId")
    void deleteByChatSessionId(@Param("chatSessionId") Long chatSessionId);
}
