package com.developer.copilot.user.dto.profilelink;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProfileLinkResponse {

    private Long id;
    private String url;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
