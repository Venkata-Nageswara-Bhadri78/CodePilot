package com.developer.copilot.community.service;

import com.developer.copilot.community.dto.response.CommunityListResponse;
import com.developer.copilot.community.dto.response.CommunityMembersResponse;
import com.developer.copilot.community.dto.response.CommunityMembershipResponse;
import com.developer.copilot.community.dto.response.CommunitySummaryResponse;
import com.developer.copilot.community.entity.CommunityMembership;

public interface CommunityMembershipService {

    CommunityListResponse listMine();

    CommunitySummaryResponse details(Long communityId);

    CommunityMembersResponse members(Long communityId);

    CommunityMembershipResponse status(Long communityId);

    CommunityMembershipResponse join(Long communityId);

    CommunityMembershipResponse leave(Long communityId);

    /**
     * Active membership of the signed-in user.
     * Message history and live delivery call this instead of copying the check.
     * A missing community account or stale terms is forbidden. A missing community is
     * unavailable. A missing or left membership is forbidden.
     */
    CommunityMembership requireActiveMembership(Long communityId);
}
