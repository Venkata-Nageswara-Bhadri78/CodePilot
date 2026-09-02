package com.developer.copilot.auth.repository;

import java.util.Optional;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.EmailVerification;

import jakarta.persistence.LockModeType;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerification> findTopByUserEmailOrderByCreatedAtDesc(String email);

    @Transactional
    void deleteByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("delete from EmailVerification ev where ev.expiresAt < :now or ev.verified = true")
    int deleteExpiredOrVerified(@Param("now") LocalDateTime now);
}