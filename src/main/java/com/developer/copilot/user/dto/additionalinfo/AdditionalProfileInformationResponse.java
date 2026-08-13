package com.developer.copilot.user.dto.additionalinfo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdditionalProfileInformationResponse {

    private Long id;
    private String type;
    private String description;
    private String link;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
