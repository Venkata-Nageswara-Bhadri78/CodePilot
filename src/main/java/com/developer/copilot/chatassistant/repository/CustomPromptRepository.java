package com.developer.copilot.chatassistant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.developer.copilot.chatassistant.entity.CustomPrompt;

public interface CustomPromptRepository extends JpaRepository<CustomPrompt, Long> {

    List<CustomPrompt> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<CustomPrompt> findByIdAndUserId(Long id, Long userId);
}
