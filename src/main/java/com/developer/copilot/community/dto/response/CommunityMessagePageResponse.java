package com.developer.copilot.community.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "One page of community text history, oldest message in this page first.")
public class CommunityMessagePageResponse {

    @Schema(example = "10")
    private Long communityId;

    private List<CommunityMessageResponse> messages;

    @Schema(description = "Pass this as cursor to load messages older than this page. Null when hasMore is false.")
    private String nextCursor;

    @Schema(description = "True when an older page exists.", example = "true")
    private boolean hasMore;
}
