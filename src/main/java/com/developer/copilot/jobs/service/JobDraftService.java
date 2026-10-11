package com.developer.copilot.jobs.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobDraftUpdateRequest;
import com.developer.copilot.jobs.dto.JobDraftWrite;
import com.developer.copilot.jobs.dto.JobResponse;

public interface JobDraftService {

    JobDraftResponse saveFallback(JobDraftWrite write);

    void deleteMatchingQuietly(String canonicalSourceUrl);

    Page<JobDraftResponse> list(Pageable pageable);

    JobDraftResponse get(Long id);

    JobDraftResponse update(Long id, JobDraftUpdateRequest request);

    /**
     * Saves the draft as a job, then deletes the draft. If the job save fails, the draft stays.
     */
    JobResponse saveAsJob(Long id);

    void delete(Long id);
}
