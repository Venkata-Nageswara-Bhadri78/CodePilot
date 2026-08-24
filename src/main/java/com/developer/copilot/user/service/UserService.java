package com.developer.copilot.user.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.developer.copilot.user.dto.ResumeDownload;
import com.developer.copilot.user.dto.ResumeResponse;
import com.developer.copilot.user.dto.ResumeUploadResponse;

public interface UserService {
    ResumeUploadResponse uploadResume(MultipartFile file);
    List<ResumeResponse> getAllResumes();
    ResumeDownload downloadResume(Long resumeId);
    void deleteResume(Long resumeId);
    void setHighPriorityResume(Long resumeId);
}