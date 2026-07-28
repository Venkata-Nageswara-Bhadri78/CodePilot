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
import com.developer.copilot.user.dto.ResumeResponse;
import com.developer.copilot.user.dto.ResumeUploadResponse;
import com.developer.copilot.user.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @PostMapping(
            value = "/resumes",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<ResumeUploadResponse>> uploadResume(
            @RequestParam("file") MultipartFile file) {

        ResumeUploadResponse uploadResponse =
                userService.uploadResume(file);

        return ResponseEntity.ok(
                ApiResponse.<ResumeUploadResponse>builder()
                        .success(true)
                        .message("Resume uploaded successfully.")
                        .data(uploadResponse)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

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

    @GetMapping("/resumes/{resumeId}")
    public ResponseEntity<Resource> downloadResume(
            @PathVariable Long resumeId) {

        Resource resource = userService.downloadResume(resumeId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"resume.pdf\""
                )
                .body(resource);
    }

    @DeleteMapping("/resumes/{resumeId}")
    public ResponseEntity<ApiResponse<Void>> deleteResume(
            @PathVariable Long resumeId) {

        userService.deleteResume(resumeId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Resume deleted successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PatchMapping("/resumes/{resumeId}/high-priority")
    public ResponseEntity<ApiResponse<Void>> setHighPriorityResume(
            @PathVariable Long resumeId) {

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