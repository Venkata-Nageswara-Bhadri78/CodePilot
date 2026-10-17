package com.developer.copilot.community.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.community.dto.CommunityMembershipOutcome;
import com.developer.copilot.community.dto.request.MembershipCommandRequest;
import com.developer.copilot.community.dto.response.CommunityListResponse;
import com.developer.copilot.community.dto.response.CommunityMembersResponse;
import com.developer.copilot.community.dto.response.CommunityMembershipResponse;
import com.developer.copilot.community.dto.response.CommunitySummaryResponse;
import com.developer.copilot.community.service.CommunityMembershipService;
import com.developer.copilot.community.service.CommunityMessages;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

@Tag(name = "Community",
        description = "List, inspect, join, and leave communities the signed-in user may access. "
                + "A URL match does not join them. Leaving does not delete the community or its messages.")
@Validated
@RestController
@RequestMapping("/api/v1/community")
@SecurityRequirement(name = "Bearer Authentication")
public class CommunityMembershipController {

    private final CommunityMembershipService communityMembershipService;

    public CommunityMembershipController(CommunityMembershipService communityMembershipService) {
        this.communityMembershipService = communityMembershipService;
    }

    @Operation(summary = "List communities the current user belongs to",
            description = "Requires a current community account. Returns only communities where this user is an active member. "
                    + "It is not a directory of every community.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Communities the caller belongs to"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Community account, terms, extension client, or incomplete username",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/memberships")
    public ResponseEntity<ApiResponse<CommunityListResponse>> listMine() {
        return ResponseEntity.ok(envelope(CommunityMessages.LISTED, communityMembershipService.listMine()));
    }

    @Operation(summary = "Read a community the current user belongs to",
            description = "Requires an active membership. Returns the shared header only: name, title, company, location, "
                    + "description, and the active member count. A departed member receives membership required.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Shared community header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Community id is not a positive number",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account, terms, or active membership required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Community is not available",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/{communityId}")
    public ResponseEntity<ApiResponse<CommunitySummaryResponse>> details(
            @Parameter(description = "Community id returned by resolve or create.", example = "10")
            @PathVariable("communityId") @Positive(message = CommunityMessages.INVALID_COMMUNITY_ID) Long communityId) {
        return ResponseEntity.ok(envelope(CommunityMessages.DETAILS, communityMembershipService.details(communityId)));
    }

    @Operation(summary = "List active members",
            description = "Requires an active membership. Each person is user id, username, and full name. "
                    + "Email is not returned. People who have left are omitted.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Active members and count"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account, terms, or active membership required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Community is not available",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/{communityId}/members")
    public ResponseEntity<ApiResponse<CommunityMembersResponse>> members(
            @PathVariable("communityId") @Positive(message = CommunityMessages.INVALID_COMMUNITY_ID) Long communityId) {
        return ResponseEntity.ok(envelope(CommunityMessages.MEMBERS, communityMembershipService.members(communityId)));
    }

    @Operation(summary = "Read the current user's membership",
            description = "Requires a current community account. Does not require membership. "
                    + "Does not return the member list or the shared description.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Caller membership. NOT_A_MEMBER is success."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Community account or terms required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Community is not available",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/{communityId}/membership")
    public ResponseEntity<ApiResponse<CommunityMembershipResponse>> status(
            @PathVariable("communityId") @Positive(message = CommunityMessages.INVALID_COMMUNITY_ID) Long communityId) {
        CommunityMembershipResponse status = communityMembershipService.status(communityId);
        return ResponseEntity.ok(envelope(status.getMessage(), status));
    }

    @Operation(summary = "Join a community",
            description = "Requires a current community account. Uses the signed-in user. "
                    + "Creates a membership, returns the existing one, or reactivates a membership the user had left. "
                    + "A repeated join does not create a second row. Send no body fields.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "First join"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Already a member, or rejoined the same row"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unknown JSON field",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Community account or terms required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Community is not available",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/{communityId}/join")
    public ResponseEntity<ApiResponse<CommunityMembershipResponse>> join(
            @PathVariable("communityId") @Positive(message = CommunityMessages.INVALID_COMMUNITY_ID) Long communityId,
            @RequestBody(required = false) MembershipCommandRequest request) {
        rejectUnused(request);
        CommunityMembershipResponse joined = communityMembershipService.join(communityId);
        HttpStatus status = joined.getOutcome() == CommunityMembershipOutcome.JOINED
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(envelope(joined.getMessage(), joined));
    }

    @Operation(summary = "Leave a community",
            description = "Leaves only the signed-in user's membership. A repeated leave is safe and keeps the first leave time. "
                    + "The community, its messages, and the recorded creator stay. "
                    + "The creator may leave. The community stays open, including when they were the last active member.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Left, already left, or was never a member"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unknown JSON field",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Community account or terms required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Community is not available",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/{communityId}/leave")
    public ResponseEntity<ApiResponse<CommunityMembershipResponse>> leave(
            @PathVariable("communityId") @Positive(message = CommunityMessages.INVALID_COMMUNITY_ID) Long communityId,
            @RequestBody(required = false) MembershipCommandRequest request) {
        rejectUnused(request);
        CommunityMembershipResponse left = communityMembershipService.leave(communityId);
        return ResponseEntity.ok(envelope(left.getMessage(), left));
    }

    private static void rejectUnused(MembershipCommandRequest request) {
        // Binding rejects unknown fields, including a user id. A missing body is valid.
        if (request == null) {
            return;
        }
    }

    private static <T> ApiResponse<T> envelope(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
