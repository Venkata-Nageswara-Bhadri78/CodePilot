package com.developer.copilot.community.websocket.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityPresenceSnapshot {

    private Long communityId;
    private List<CommunityPresenceEvent> online;
}
