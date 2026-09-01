package com.developer.copilot.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long>{

    Optional<RefreshToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);
    List<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);
    @Transactional
    void deleteByUserId(Long userId);
    @Transactional
    void deleteByUserIdAndRevokedFalse(Long userId);
    @Transactional 
    void deleteByToken(String token);
}
