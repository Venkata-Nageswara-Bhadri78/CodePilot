package com.developer.copilot.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);

    @Transactional
    void deleteByUserIdAndUsedFalse(Long userId);

    @Modifying
    @Transactional
    @Query("delete from PasswordResetToken t where t.used = true or t.expiresAt < :now")
    int deleteUsedOrExpired(@Param("now") java.time.LocalDateTime now);
}