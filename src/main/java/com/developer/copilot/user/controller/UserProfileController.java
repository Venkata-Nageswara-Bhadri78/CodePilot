package com.developer.copilot.user.controller;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.user.dto.additionalinfo.AdditionalProfileInformationRequest;
import com.developer.copilot.user.dto.additionalinfo.AdditionalProfileInformationResponse;
import com.developer.copilot.user.dto.education.EducationRequest;
import com.developer.copilot.user.dto.education.EducationResponse;
import com.developer.copilot.user.dto.experience.WorkExperienceRequest;
import com.developer.copilot.user.dto.experience.WorkExperienceResponse;
import com.developer.copilot.user.dto.profile.UserProfileRequest;
import com.developer.copilot.user.dto.profile.UserProfileResponse;
import com.developer.copilot.user.dto.profilelink.ProfileLinkRequest;
import com.developer.copilot.user.dto.profilelink.ProfileLinkResponse;
import com.developer.copilot.user.dto.project.ProjectRequest;
import com.developer.copilot.user.dto.project.ProjectResponse;
import com.developer.copilot.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "User - Profile", description = "User profile and career information")
@RestController
@RequestMapping("/api/v1/users/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class UserProfileController {

    private final UserProfileService userProfileService;

    // ─── Profile ─────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> createProfile(
            @Valid @RequestBody UserProfileRequest request) {

        UserProfileResponse data = userProfileService.createProfile(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<UserProfileResponse>builder()
                        .success(true)
                        .message("Profile created successfully.")
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {

        return ResponseEntity.ok(
                ApiResponse.<UserProfileResponse>builder()
                        .success(true)
                        .message("Profile fetched successfully.")
                        .data(userProfileService.getProfile())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UserProfileRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<UserProfileResponse>builder()
                        .success(true)
                        .message("Profile updated successfully.")
                        .data(userProfileService.updateProfile(request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteProfile() {

        userProfileService.deleteProfile();

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Profile deleted successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    // ─── Work Experience ──────────────────────────────────────────────────────

    @PostMapping("/experiences")
    public ResponseEntity<ApiResponse<WorkExperienceResponse>> addWorkExperience(
            @Valid @RequestBody WorkExperienceRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<WorkExperienceResponse>builder()
                        .success(true)
                        .message("Work experience added successfully.")
                        .data(userProfileService.addWorkExperience(request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @GetMapping("/experiences")
    public ResponseEntity<ApiResponse<List<WorkExperienceResponse>>> getWorkExperiences() {

        return ResponseEntity.ok(
                ApiResponse.<List<WorkExperienceResponse>>builder()
                        .success(true)
                        .message("Work experiences fetched successfully.")
                        .data(userProfileService.getWorkExperiences())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PutMapping("/experiences/{id}")
    public ResponseEntity<ApiResponse<WorkExperienceResponse>> updateWorkExperience(
            @PathVariable Long id,
            @Valid @RequestBody WorkExperienceRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<WorkExperienceResponse>builder()
                        .success(true)
                        .message("Work experience updated successfully.")
                        .data(userProfileService.updateWorkExperience(id, request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @DeleteMapping("/experiences/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWorkExperience(@PathVariable Long id) {

        userProfileService.deleteWorkExperience(id);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Work experience deleted successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    // ─── Education ────────────────────────────────────────────────────────────

    @PostMapping("/educations")
    public ResponseEntity<ApiResponse<EducationResponse>> addEducation(
            @Valid @RequestBody EducationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<EducationResponse>builder()
                        .success(true)
                        .message("Education added successfully.")
                        .data(userProfileService.addEducation(request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @GetMapping("/educations")
    public ResponseEntity<ApiResponse<List<EducationResponse>>> getEducations() {

        return ResponseEntity.ok(
                ApiResponse.<List<EducationResponse>>builder()
                        .success(true)
                        .message("Education records fetched successfully.")
                        .data(userProfileService.getEducations())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PutMapping("/educations/{id}")
    public ResponseEntity<ApiResponse<EducationResponse>> updateEducation(
            @PathVariable Long id,
            @Valid @RequestBody EducationRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<EducationResponse>builder()
                        .success(true)
                        .message("Education updated successfully.")
                        .data(userProfileService.updateEducation(id, request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @DeleteMapping("/educations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEducation(@PathVariable Long id) {

        userProfileService.deleteEducation(id);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Education deleted successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    // ─── Projects ────────────────────────────────────────────────────────────

    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<ProjectResponse>> addProject(
            @Valid @RequestBody ProjectRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ProjectResponse>builder()
                        .success(true)
                        .message("Project added successfully.")
                        .data(userProfileService.addProject(request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getProjects() {

        return ResponseEntity.ok(
                ApiResponse.<List<ProjectResponse>>builder()
                        .success(true)
                        .message("Projects fetched successfully.")
                        .data(userProfileService.getProjects())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PutMapping("/projects/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<ProjectResponse>builder()
                        .success(true)
                        .message("Project updated successfully.")
                        .data(userProfileService.updateProject(id, request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long id) {

        userProfileService.deleteProject(id);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Project deleted successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    // ─── Additional Information ───────────────────────────────────────────────

    @PostMapping("/additional-info")
    public ResponseEntity<ApiResponse<AdditionalProfileInformationResponse>> addAdditionalInformation(
            @Valid @RequestBody AdditionalProfileInformationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<AdditionalProfileInformationResponse>builder()
                        .success(true)
                        .message("Additional information added successfully.")
                        .data(userProfileService.addAdditionalInformation(request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @GetMapping("/additional-info")
    public ResponseEntity<ApiResponse<List<AdditionalProfileInformationResponse>>> getAdditionalInformation() {

        return ResponseEntity.ok(
                ApiResponse.<List<AdditionalProfileInformationResponse>>builder()
                        .success(true)
                        .message("Additional information fetched successfully.")
                        .data(userProfileService.getAdditionalInformation())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PutMapping("/additional-info/{id}")
    public ResponseEntity<ApiResponse<AdditionalProfileInformationResponse>> updateAdditionalInformation(
            @PathVariable Long id,
            @Valid @RequestBody AdditionalProfileInformationRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<AdditionalProfileInformationResponse>builder()
                        .success(true)
                        .message("Additional information updated successfully.")
                        .data(userProfileService.updateAdditionalInformation(id, request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @DeleteMapping("/additional-info/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAdditionalInformation(@PathVariable Long id) {

        userProfileService.deleteAdditionalInformation(id);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Additional information deleted successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    // ─── Profile Links ────────────────────────────────────────────────────────

    @PostMapping("/links")
    public ResponseEntity<ApiResponse<ProfileLinkResponse>> addProfileLink(
            @Valid @RequestBody ProfileLinkRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ProfileLinkResponse>builder()
                        .success(true)
                        .message("Profile link added successfully.")
                        .data(userProfileService.addProfileLink(request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @GetMapping("/links")
    public ResponseEntity<ApiResponse<List<ProfileLinkResponse>>> getProfileLinks() {

        return ResponseEntity.ok(
                ApiResponse.<List<ProfileLinkResponse>>builder()
                        .success(true)
                        .message("Profile links fetched successfully.")
                        .data(userProfileService.getProfileLinks())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PutMapping("/links/{id}")
    public ResponseEntity<ApiResponse<ProfileLinkResponse>> updateProfileLink(
            @PathVariable Long id,
            @Valid @RequestBody ProfileLinkRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<ProfileLinkResponse>builder()
                        .success(true)
                        .message("Profile link updated successfully.")
                        .data(userProfileService.updateProfileLink(id, request))
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @DeleteMapping("/links/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProfileLink(@PathVariable Long id) {

        userProfileService.deleteProfileLink(id);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Profile link deleted successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

}
