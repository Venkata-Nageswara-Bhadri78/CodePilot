package com.developer.copilot.community.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.developer.copilot.community.entity.Community;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    Optional<Community> findByCanonicalPostingKey(String canonicalPostingKey);

    boolean existsByCanonicalPostingKey(String canonicalPostingKey);

    boolean existsByCommunityName(String communityName);
}
