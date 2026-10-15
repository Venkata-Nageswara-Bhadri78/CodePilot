package com.developer.copilot.auth.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.PasswordSetupOtp;

import jakarta.persistence.LockModeType;

public interface PasswordSetupOtpRepository extends JpaRepository<PasswordSetupOtp, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordSetupOtp> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    @Transactional
    void deleteByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("delete from PasswordSetupOtp otp where otp.expiresAt < :now or otp.used = true")
    int deleteExpiredOrUsed(@Param("now") LocalDateTime now);
}
