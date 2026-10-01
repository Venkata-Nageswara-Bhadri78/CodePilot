package com.developer.copilot.chatassistant.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.chatassistant.dto.request.CustomPromptRequest;
import com.developer.copilot.chatassistant.dto.response.CustomPromptResponse;
import com.developer.copilot.chatassistant.service.CustomPromptService;
import com.developer.copilot.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Custom Prompts",
        description = "Reusable prompts owned by the signed-in user. "
                + "Obtain a JWT from POST /api/v1/auth/login, then Authorize. "
                + "List/get/update/delete only return this user's rows. "
                + "Someone else's prompt id looks like 404.")
@RestController
@RequestMapping("/api/v1/custom-prompts")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "Bearer Authentication")
public class CustomPromptController {

    private static final String PROMPT_ID_DESCRIPTION =
            "Custom prompt id owned by the current user; foreign ids look like 404";

    private static final String ERROR_JSON =
            "{\"success\":false,\"message\":\"A human-readable description of what went wrong.\","
                    + "\"data\":null,\"timestamp\":\"2026-01-01T12:00:00\"}";

    private final CustomPromptService customPromptService;

    @Operation(
            summary = "Create a custom prompt",
            description = "Stores a new reusable prompt for the current user. Title and prompt are required. "
                    + "Titles are not unique. Does not send anything to Gemini.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Custom prompt created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Blank/oversized title or prompt, or illegal JSON",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CustomPromptResponse>> create(
            @Valid @RequestBody CustomPromptRequest request) {

        CustomPromptResponse created = customPromptService.create(request);

        ApiResponse<CustomPromptResponse> response = ApiResponse.<CustomPromptResponse>builder()
                .success(true)
                .message("Custom prompt created successfully.")
                .data(created)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "List my custom prompts",
            description = "Returns every custom prompt owned by the current user, newest-updated first. "
                    + "Empty array when none exist — this is not an error. Not paginated. "
                    + "Does not send anything to Gemini.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Custom prompts returned (may be an empty array)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomPromptResponse>>> listMine() {
        List<CustomPromptResponse> prompts = customPromptService.listMine();

        ApiResponse<List<CustomPromptResponse>> response = ApiResponse.<List<CustomPromptResponse>>builder()
                .success(true)
                .message("Custom prompts retrieved successfully.")
                .data(prompts)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get a custom prompt by id",
            description = "Returns one custom prompt owned by the current user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custom prompt returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid custom prompt id",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Prompt not found or not owned by the current user",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Custom prompt not found.\","
                                    + "\"data\":null,\"timestamp\":\"2026-01-01T12:00:00\"}")))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomPromptResponse>> getById(
            @Parameter(description = PROMPT_ID_DESCRIPTION, example = "7", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @PathVariable Long id) {

        CustomPromptResponse prompt = customPromptService.getById(id);

        ApiResponse<CustomPromptResponse> response = ApiResponse.<CustomPromptResponse>builder()
                .success(true)
                .message("Custom prompt retrieved successfully.")
                .data(prompt)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Replace a custom prompt",
            description = "Fully replaces title and prompt. Both fields are required again.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custom prompt updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Blank/oversized title or prompt, illegal JSON, or invalid id",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Prompt not found or not owned by the current user",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomPromptResponse>> update(
            @Parameter(description = PROMPT_ID_DESCRIPTION, example = "7", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @PathVariable Long id,
            @Valid @RequestBody CustomPromptRequest request) {

        CustomPromptResponse updated = customPromptService.update(id, request);

        ApiResponse<CustomPromptResponse> response = ApiResponse.<CustomPromptResponse>builder()
                .success(true)
                .message("Custom prompt updated successfully.")
                .data(updated)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Delete a custom prompt",
            description = "Permanently deletes one custom prompt owned by the current user. Not idempotent — "
                    + "a missing or foreign id is 404.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custom prompt deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid custom prompt id",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Prompt not found or not owned by the current user",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = PROMPT_ID_DESCRIPTION, example = "7", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @PathVariable Long id) {

        customPromptService.delete(id);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Custom prompt deleted successfully.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }
}
