package com.developer.copilot.community.websocket.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityMembershipSocketEvent {

    private Long communityId;
    private Long userId;
    private String membership;
}
