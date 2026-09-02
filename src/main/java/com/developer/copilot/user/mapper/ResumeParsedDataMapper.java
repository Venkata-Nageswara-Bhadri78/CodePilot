package com.developer.copilot.user.mapper;

import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.service.parsing.ResumeContextTextRenderer;
import com.developer.copilot.user.service.parsing.ResumeSectionsCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ResumeParsedDataMapper {

    private final ResumeSectionsCodec resumeSectionsCodec;
    private final ResumeContextTextRenderer resumeContextTextRenderer;

    /**
     * The resume is passed explicitly rather than read from
     * {@link ResumeParsedData#getResume()} because parsed records are frequently
     * detached, and their resume association is lazy.
     */
    public ResumeParsedDataResponse toResponse(Resume resume, ResumeParsedData parsedData) {

        Map<String, String> sections = resumeSectionsCodec.fromJson(parsedData.getSectionsJson());

        return ResumeParsedDataResponse.builder()
                .resumeId(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .highPriority(resume.getHighPriority())
                .status(parsedData.getStatus().name())
                .attemptCount(parsedData.getAttemptCount())
                .lastError(parsedData.getLastError())
                .parserVersion(parsedData.getParserVersion())
                .parsedAt(parsedData.getParsedAt())
                .pageCount(parsedData.getPageCount())
                .characterCount(parsedData.getCharacterCount())
                .truncated(parsedData.getTruncated())
                .candidateName(parsedData.getCandidateName())
                .email(parsedData.getEmail())
                .phone(parsedData.getPhone())
                .location(parsedData.getLocation())
                .linkedinUrl(parsedData.getLinkedinUrl())
                .githubUrl(parsedData.getGithubUrl())
                .sections(sections)
                .rawText(parsedData.getRawText())
                .contextText(resumeContextTextRenderer.render(parsedData, sections))
                .build();
    }
}
