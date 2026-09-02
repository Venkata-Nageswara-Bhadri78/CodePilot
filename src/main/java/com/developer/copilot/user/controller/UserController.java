package com.developer.copilot.user.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.user.dto.ResumeDownload;
import com.developer.copilot.user.dto.ResumeResponse;
import com.developer.copilot.user.dto.ResumeUploadResponse;
import com.developer.copilot.user.service.UserService;
import com.developer.copilot.user.util.ResumeFilenameUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "User - Resumes", description = "Resume upload, download, and management")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Upload resume",
            description = "PDF only, max 5MB (`resume.max-file-size-mb`), max 10 per user. "
                    + "Multipart field name is `file`. Profile must exist. First upload becomes primary. "
                    + "Parse runs in the background — list/upload do not include parse status. "
                    + "Returns 201. 429 + Retry-After when the per-user/IP upload budget is spent."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Uploaded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file, oversize, or limit exceeded", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Profile not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate resume", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Storage failure", content = @Content)
    })
    @PostMapping(
            value = "/resumes",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<ResumeUploadResponse>> uploadResume(
            @Parameter(
                    description = "PDF resume file",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
            @RequestParam("file") MultipartFile file) {

        ResumeUploadResponse uploadResponse = userService.uploadResume(file);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ResumeUploadResponse>builder()
                        .success(true)
                        .message("Resume uploaded successfully.")
                        .data(uploadResponse)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Operation(summary = "List resumes", description = "Active resumes only. No parse status.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No profile", content = @Content)
    })
    @GetMapping("/resumes")
    public ResponseEntity<ApiResponse<List<ResumeResponse>>> getAllResumes() {

        return ResponseEntity.ok(
                ApiResponse.<List<ResumeResponse>>builder()
                        .success(true)
                        .message("Resumes fetched successfully.")
                        .data(userService.getAllResumes())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Operation(
            summary = "Download resume",
            description = "Returns the PDF file bytes, not a JSON wrapper. "
                    + "Content-Disposition filename is the sanitized original name (allowlist [A-Za-z0-9._-], else resume.pdf)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "PDF file",
                    content = @Content(
                            mediaType = "application/pdf",
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
    })
    @GetMapping("/resumes/{resumeId}")
    public ResponseEntity<Resource> downloadResume(@PathVariable Long resumeId) {

        ResumeDownload download = userService.downloadResume(resumeId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + ResumeFilenameUtil.sanitizeForDownload(download.filename()) + "\""
                )
                .body(download.resource());
    }

    @Operation(
            summary = "Delete resume",
            description = "Hard-deletes the row so the same PDF checksum can be uploaded again. "
                    + "429 + Retry-After when the per-user/IP delete budget is spent."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Resume not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Storage failure", content = @Content)
    })
    @DeleteMapping("/resumes/{resumeId}")
    public ResponseEntity<ApiResponse<Void>> deleteResume(@PathVariable Long resumeId) {

        userService.deleteResume(resumeId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Resume deleted successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Operation(summary = "Set high-priority resume", description = "Empty body. There is no query flag to unset primary.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Resume not found", content = @Content)
    })
    @PatchMapping("/resumes/{resumeId}/high-priority")
    public ResponseEntity<ApiResponse<Void>> setHighPriorityResume(@PathVariable Long resumeId) {

        userService.setHighPriorityResume(resumeId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("High priority resume updated successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }
}
