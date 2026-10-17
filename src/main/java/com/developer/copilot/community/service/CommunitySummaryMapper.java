package com.developer.copilot.community.service;

import org.springframework.stereotype.Component;

import com.developer.copilot.community.dto.CommunityMembershipView;
import com.developer.copilot.community.dto.response.CommunitySummaryResponse;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.repository.CommunityMembershipRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommunitySummaryMapper {

    private final CommunityMembershipRepository communityMembershipRepository;

    public CommunitySummaryResponse toSummary(Community community, Long userId) {
        CommunityMembership membership = communityMembershipRepository
                .findByCommunityIdAndUserId(community.getId(), userId)
                .orElse(null);
        long activeMembers = communityMembershipRepository.countByCommunityIdAndMembershipStatus(
                community.getId(), MembershipStatus.ACTIVE);
        return toSummary(community, membership, activeMembers);
    }

    public CommunitySummaryResponse toSummary(
            Community community,
            CommunityMembership membership,
            long activeMemberCount) {
        boolean active = membership != null && membership.getMembershipStatus() == MembershipStatus.ACTIVE;
        return CommunitySummaryResponse.builder()
                .id(community.getId())
                .communityName(community.getCommunityName())
                .title(community.getTitle())
                .company(community.getCompany())
                .location(community.getLocation())
                .description(community.getDescription())
                .createdAt(community.getCreatedAt())
                .membership(active ? CommunityMembershipView.MEMBER : CommunityMembershipView.NOT_A_MEMBER)
                .activeMemberCount(activeMemberCount)
                .build();
    }
}
