package com.developer.copilot.jobs.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.jobs.dto.JobDraftWrite;
import com.developer.copilot.jobs.entity.JobDraftEntity;
import com.developer.copilot.jobs.mapper.JobDraftMapper;
import com.developer.copilot.jobs.repository.JobDraftRepository;

import lombok.RequiredArgsConstructor;

/**
 * Independent transactions for draft upsert and cleanup. Callers that just failed a
 * jobs save must not share that rolled-back transaction.
 */
@Component
@RequiredArgsConstructor
public class JobDraftStore {

    private final JobDraftRepository jobDraftRepository;
    private final JobDraftMapper jobDraftMapper;
    private final CurrentUserService currentUserService;
    private final UrlNormalizationUtil urlNormalizationUtil;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobDraftEntity upsert(JobDraftWrite write) {
        User user = currentUserService.getCurrentUser();
        String normalizedUrl = null;
        String urlHash = null;
        if (write.getSourceUrl() != null && !write.getSourceUrl().isBlank()) {
            normalizedUrl = urlNormalizationUtil.normalizeStrict(write.getSourceUrl());
            urlHash = urlNormalizationUtil.sha256Hex(normalizedUrl);
        }

        JobDraftEntity entity = null;
        if (urlHash != null) {
            entity = jobDraftRepository.findByUserIdAndSourceUrlHash(user.getId(), urlHash).orElse(null);
        }
        if (entity == null) {
            entity = new JobDraftEntity();
            entity.setUser(user);
        }
        jobDraftMapper.applyWrite(entity, write, normalizedUrl, urlHash);
        return jobDraftRepository.saveAndFlush(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteByCanonicalUrl(String canonicalSourceUrl) {
        User user = currentUserService.getCurrentUser();
        String normalizedUrl = urlNormalizationUtil.normalizeStrict(canonicalSourceUrl);
        String urlHash = urlNormalizationUtil.sha256Hex(normalizedUrl);
        jobDraftRepository.deleteByUserIdAndSourceUrlHash(user.getId(), urlHash);
    }
}
