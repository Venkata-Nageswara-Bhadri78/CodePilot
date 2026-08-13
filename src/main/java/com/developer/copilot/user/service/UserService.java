package com.developer.copilot.user.service;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.developer.copilot.user.dto.ResumeResponse;
import com.developer.copilot.user.dto.ResumeUploadResponse;

public interface UserService {
    ResumeUploadResponse uploadResume(MultipartFile file);
    List<ResumeResponse> getAllResumes();
    Resource downloadResume(Long resumeId);
    void deleteResume(Long resumeId);
    void setHighPriorityResume(Long resumeId);
}