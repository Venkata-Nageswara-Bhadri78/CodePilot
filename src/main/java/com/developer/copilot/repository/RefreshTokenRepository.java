package com.developer.copilot.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import com.developer.copilot.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long>{

    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);
    @Transactional
    void deleteByUserId(Long userId);
    @Transactional
    void deleteByUserIdAndRevokedFalse(Long userId);
    @Transactional 
    void deleteByToken(String token);
}
