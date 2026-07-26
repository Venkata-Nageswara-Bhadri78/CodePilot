package com.developer.copilot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);
    @Transactional
    void deleteByUserIdAndUsedFalse(Long userId);
}