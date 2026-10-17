package com.developer.copilot.community.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;

public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long> {

    /**
     * Loads the community and the user with the row so callers can read them after the
     * transaction that fetched the membership has ended.
     */
    @EntityGraph(attributePaths = {"community", "user"})
    Optional<CommunityMembership> findByCommunityIdAndUserId(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);

    long countByCommunityIdAndMembershipStatus(Long communityId, MembershipStatus membershipStatus);

    @EntityGraph(attributePaths = "community")
    List<CommunityMembership> findByUserIdAndMembershipStatusOrderByJoinedAtDescIdDesc(
            Long userId, MembershipStatus membershipStatus);

    @EntityGraph(attributePaths = "user")
    List<CommunityMembership> findByCommunityIdAndMembershipStatusOrderByJoinedAtAscIdAsc(
            Long communityId, MembershipStatus membershipStatus);
}
