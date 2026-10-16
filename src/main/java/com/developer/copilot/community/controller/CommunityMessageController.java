package com.developer.copilot.community.controller;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.community.dto.response.CommunityMessagePageResponse;
import com.developer.copilot.community.service.CommunityMessageService;
import com.developer.copilot.community.service.CommunityMessages;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Tag(name = "Community",
        description = "Text history for a community the signed-in user belongs to. "
                + "Sending a live message is not this route.")
@Validated
@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class CommunityMessageController {

    private final CommunityMessageService communityMessageService;

    @Operation(summary = "Read a page of community messages",
            description = "Requires an active membership. Omit cursor to load the newest page. "
                    + "Pass nextCursor to load the next older page. Messages in the page are oldest first. "
                    + "The group header and member list stay on the community and members routes. "
                    + "limit defaults to app.community.message-page-size and cannot exceed max-message-page-size.")
    @GetMapping("/{communityId}/messages")
    public ResponseEntity<ApiResponse<CommunityMessagePageResponse>> history(
            @Parameter(description = "Community id.", example = "10")
            @PathVariable("communityId") @Positive(message = CommunityMessages.INVALID_COMMUNITY_ID) Long communityId,
            @Parameter(description = "Page size. Omit it to use the server default.")
            @RequestParam(name = "limit", required = false) Integer limit,
            @Parameter(description = "Cursor from the previous page. Omit it for the newest messages.")
            @RequestParam(name = "cursor", required = false) String cursor) {
        CommunityMessagePageResponse page = communityMessageService.history(communityId, limit, cursor);
        return ResponseEntity.ok(ApiResponse.<CommunityMessagePageResponse>builder()
                .success(true)
                .message(CommunityMessages.HISTORY)
                .data(page)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
