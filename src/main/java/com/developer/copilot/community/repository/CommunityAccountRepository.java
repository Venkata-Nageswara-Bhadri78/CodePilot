package com.developer.copilot.community.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.developer.copilot.community.entity.CommunityAccount;

public interface CommunityAccountRepository extends JpaRepository<CommunityAccount, Long> {

    Optional<CommunityAccount> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
