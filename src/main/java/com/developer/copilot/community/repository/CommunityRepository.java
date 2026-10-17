package com.developer.copilot.community.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.developer.copilot.community.entity.Community;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    Optional<Community> findByCanonicalPostingKey(String canonicalPostingKey);

    @Query("select c from Community c join fetch c.createdBy where c.id = :id")
    Optional<Community> findWithCreatorById(@Param("id") Long id);

    boolean existsByCanonicalPostingKey(String canonicalPostingKey);

    boolean existsByCommunityName(String communityName);
}
