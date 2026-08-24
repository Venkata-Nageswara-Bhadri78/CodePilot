package com.developer.copilot.user.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
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
            description = "PDF only, max 5MB, max 10 per user. Profile must exist. First upload becomes primary."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Uploaded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file, duplicate, or limit exceeded", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Profile not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate resume", content = @Content)
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

        return ResponseEntity.ok(
                ApiResponse.<ResumeUploadResponse>builder()
                        .success(true)
                        .message("Resume uploaded successfully.")
                        .data(uploadResponse)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Operation(summary = "List resumes", description = "Returns active resumes for the current user's profile.")
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
            description = "Returns the PDF file bytes, not a JSON wrapper."
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
                        "attachment; filename=\"" + sanitizeFilename(download.filename()) + "\""
                )
                .body(download.resource());
    }

    @Operation(summary = "Delete resume")
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

    @Operation(summary = "Set high-priority resume", description = "Marks one resume as primary for the profile.")
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

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "resume.pdf";
        }
        String sanitized = filename.replaceAll("[\\r\\n\"]", "");
        return sanitized.isBlank() ? "resume.pdf" : sanitized;
    }
}
