package com.developer.copilot.jobs.service;

import com.developer.copilot.ai.dto.request.ResumeToJobScoreAiRequest;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.util.ResumeToJobScoreSupport;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.UserProfile;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.repository.ResumeRepository;
import com.developer.copilot.user.repository.UserProfileRepository;
import com.developer.copilot.user.service.ResumeParsingService;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Binds a job to one of the caller's resumes and scores that resume against the job.
 * Isolated so {@code JobServiceImpl} stays the owner of persistence while resume lookup
 * and AI scoring stay in one place (also reused by job extraction).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobResumeBindingService {

    private static final String COMPLETED_STATUS = "COMPLETED";

    private final UserProfileRepository userProfileRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeParsingService resumeParsingService;
    private final AiService aiService;

    /**
     * Resolves the resume id to store on a job. An explicit id must be an active resume
     * owned by the user. When omitted, the high-priority resume is used. Returns
     * {@code null} when the user has no profile or no active resume.
     */
    public Long resolveResumeId(User user, Long requestedResumeId) {
        UserProfile profile = userProfileRepository.findByUser(user).orElse(null);
        if (profile == null) {
            if (requestedResumeId != null) {
                throw new ResumeNotFoundException();
            }
            return null;
        }

        if (requestedResumeId != null) {
            return resumeRepository.findByIdAndUserProfileAndActiveTrue(requestedResumeId, profile)
                    .map(Resume::getId)
                    .orElseThrow(ResumeNotFoundException::new);
        }

        return resumeRepository.findByHighPriorityTrueAndUserProfileAndActiveTrue(profile)
                .map(Resume::getId)
                .orElse(null);
    }

    /**
     * Parsed resume text for prompts. Returns empty string when there is no resume,
     * parse is pending/failed, or the user has no profile — scoring then uses 0.
     */
    public String loadResumeContextQuietly(Long resumeId) {
        if (resumeId == null) {
            return "";
        }
        try {
            ResumeParsedDataResponse parsed = resumeParsingService.getParsedResume(resumeId);
            if (parsed != null
                    && COMPLETED_STATUS.equals(parsed.getStatus())
                    && StringUtils.hasText(parsed.getContextText())) {
                return parsed.getContextText().trim();
            }
        } catch (ResumeNotFoundException | UserProfileNotFoundException | ResumeParsingException ex) {
            log.info("Resume context unavailable for job scoring, resumeId={}: {}", resumeId, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Unexpected resume-context failure for job scoring, resumeId={}: {}", resumeId, ex.getMessage());
        }
        return "";
    }

    /**
     * Scores a resume against a job. Returns {@link ResumeToJobScoreSupport#DEFAULT_SCORE}
     * when there is no resume, no usable parse, or the model call fails.
     */
    public int scoreOrDefault(Long resumeId, JobEntity job) {
        try {
            return score(resumeId, job, false);
        } catch (RuntimeException ex) {
            log.warn("Resume-to-job scoring failed; defaulting to {}: {}",
                    ResumeToJobScoreSupport.DEFAULT_SCORE, ex.getMessage());
            return ResumeToJobScoreSupport.DEFAULT_SCORE;
        }
    }

    /**
     * Scores a resume against a job. Propagates AI failures so an explicit resume
     * switch can surface 502/503 instead of silently storing 0.
     */
    public int scoreOrThrow(Long resumeId, JobEntity job) {
        return score(resumeId, job, true);
    }

    /**
     * Compact job snapshot for the score-only prompt. Omits notes and status.
     */
    public String buildJobSnapshot(JobEntity job) {
        if (job == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, "title", job.getTitle());
        append(sb, "company", job.getCompany());
        append(sb, "location", job.getLocation());
        append(sb, "employmentType", job.getEmploymentType());
        append(sb, "workMode", job.getWorkMode());
        append(sb, "experience", job.getExperience());
        append(sb, "salary", job.getSalary());
        append(sb, "education", job.getEducation());
        append(sb, "department", job.getDepartment());
        append(sb, "industry", job.getIndustry());
        append(sb, "sourcePlatform", job.getSourcePlatform());
        List<String> skills = job.getSkills();
        if (skills != null && !skills.isEmpty()) {
            sb.append("skills: ")
                    .append(skills.stream().filter(StringUtils::hasText).collect(Collectors.joining(", ")))
                    .append('\n');
        }
        String description = StringUtils.hasText(job.getDescription())
                ? job.getDescription()
                : job.getOriginalDescription();
        append(sb, "description", description);
        return sb.toString();
    }

    private int score(Long resumeId, JobEntity job, boolean throwOnAiFailure) {
        if (resumeId == null) {
            return ResumeToJobScoreSupport.DEFAULT_SCORE;
        }
        String resumeContext = loadResumeContextQuietly(resumeId);
        if (!StringUtils.hasText(resumeContext)) {
            return ResumeToJobScoreSupport.DEFAULT_SCORE;
        }
        try {
            Integer raw = aiService.scoreResumeToJob(ResumeToJobScoreAiRequest.builder()
                    .resumeContext(resumeContext)
                    .jobSnapshot(buildJobSnapshot(job))
                    .build());
            return ResumeToJobScoreSupport.sanitize(raw);
        } catch (AiServiceException ex) {
            if (throwOnAiFailure) {
                throw ex;
            }
            log.warn("AI resume-to-job scoring failed; defaulting to {}: {}",
                    ResumeToJobScoreSupport.DEFAULT_SCORE, ex.getMessage());
            return ResumeToJobScoreSupport.DEFAULT_SCORE;
        }
    }

    private static void append(StringBuilder sb, String name, String value) {
        if (StringUtils.hasText(value)) {
            sb.append(name).append(": ").append(value.trim()).append('\n');
        }
    }
}
