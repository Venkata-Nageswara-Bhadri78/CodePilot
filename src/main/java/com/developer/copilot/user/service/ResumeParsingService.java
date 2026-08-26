package com.developer.copilot.user.service;

import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.entity.Resume;

import java.util.List;

/**
 * Parsed resume information for the authenticated user.
 * <p>
 * This is an in-process, service-to-service contract. Other modules inject this
 * bean directly; the internal HTTP controller is a thin wrapper over the same
 * methods for callers that are, or will become, out of process.
 */
public interface ResumeParsingService {

    /**
     * Returns the parsed information for one of the authenticated user's resumes.
     * <p>
     * Persisted results are returned as-is. When no usable result exists yet the
     * resume is parsed immediately and the caller is answered straight away, with
     * persistence happening separately afterwards.
     *
     * @param resumeId the resume to read, or {@code null} for the user's high-priority resume
     */
    ResumeParsedDataResponse getParsedResume(Long resumeId);

    /**
     * Creates the pending parsed-data record for a freshly uploaded resume and
     * queues parsing to run once the surrounding transaction commits.
     */
    void initializeAndScheduleParsing(Resume resume);

    /**
     * Removes parsed records so the owning resumes can be deleted.
     */
    void deleteParsedDataFor(Resume resume);

    void deleteParsedDataFor(List<Resume> resumes);
}
