package com.developer.copilot.user.mapper;

import com.developer.copilot.user.dto.ResumeResponse;
import com.developer.copilot.user.entity.Resume;
import org.springframework.stereotype.Component;

@Component
public class ResumeMapper {

    public ResumeResponse toResponse(Resume resume) {

        return ResumeResponse.builder()
                .id(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .fileSize(resume.getFileSize())
                .highPriority(resume.getHighPriority())
                .build();

    }

}