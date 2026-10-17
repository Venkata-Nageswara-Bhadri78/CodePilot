package com.developer.copilot.community.websocket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "A person became online or offline in one community. Email is not included.")
public class CommunityPresenceEvent {

    public static final String ONLINE = "ONLINE";
    public static final String OFFLINE = "OFFLINE";

    @Schema(example = "10")
    private Long communityId;

    @Schema(example = "7")
    private Long userId;

    @Schema(example = "ada")
    private String username;

    @Schema(example = "Ada Lovelace")
    private String fullName;

    @Schema(example = "ONLINE")
    private String status;
}
