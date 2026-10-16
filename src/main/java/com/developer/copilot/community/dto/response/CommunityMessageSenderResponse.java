package com.developer.copilot.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Who sent a community message. Email and other private profile fields are not included.")
public class CommunityMessageSenderResponse {

    @Schema(description = "users.id. The same id used on the member list.", example = "7")
    private Long userId;

    @Schema(example = "ada")
    private String username;

    @Schema(example = "Ada Lovelace")
    private String fullName;
}
