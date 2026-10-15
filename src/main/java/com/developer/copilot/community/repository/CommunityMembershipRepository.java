package com.developer.copilot.community.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;

public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long> {

    Optional<CommunityMembership> findByCommunityIdAndUserId(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);

    long countByCommunityIdAndMembershipStatus(Long communityId, MembershipStatus membershipStatus);
}
