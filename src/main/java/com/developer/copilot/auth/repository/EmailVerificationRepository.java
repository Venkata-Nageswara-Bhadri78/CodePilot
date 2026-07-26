package com.developer.copilot.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.EmailVerification;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findTopByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<EmailVerification> findTopByUserEmailOrderByCreatedAtDesc(String email);
    @Transactional
    void deleteByUserId(Long userId);
}