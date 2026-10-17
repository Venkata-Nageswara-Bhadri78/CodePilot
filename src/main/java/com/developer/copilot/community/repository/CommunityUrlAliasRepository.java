package com.developer.copilot.community.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.developer.copilot.community.entity.CommunityUrlAlias;

public interface CommunityUrlAliasRepository extends JpaRepository<CommunityUrlAlias, Long> {

    Optional<CommunityUrlAlias> findByNormalizedUrlHash(String normalizedUrlHash);

    boolean existsByNormalizedUrlHash(String normalizedUrlHash);

    List<CommunityUrlAlias> findByCommunityId(Long communityId);
}
