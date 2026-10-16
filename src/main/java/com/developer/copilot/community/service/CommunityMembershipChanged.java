package com.developer.copilot.community.service;

import com.developer.copilot.community.entity.MembershipStatus;

/**
 * A membership row changed. Live delivery listens so a person who left stops receiving that community.
 */
public record CommunityMembershipChanged(Long communityId, Long userId, MembershipStatus status) {
}
