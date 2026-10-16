package com.developer.copilot.community.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.community.config.CommunityAccountProperties;
import com.developer.copilot.community.dto.CommunityMatchOutcome;
import com.developer.copilot.community.dto.CommunityMembershipView;
import com.developer.copilot.community.dto.request.CreateCommunityRequest;
import com.developer.copilot.community.dto.request.ResolveCommunityRequest;
import com.developer.copilot.community.dto.response.CommunityCreateResponse;
import com.developer.copilot.community.dto.response.CommunityDescriptionResponse;
import com.developer.copilot.community.dto.response.CommunityMatchResponse;
import com.developer.copilot.community.dto.response.CommunityPrefillResponse;
import com.developer.copilot.community.dto.response.CommunitySummaryResponse;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.exception.CommunityAliasConflictException;
import com.developer.copilot.community.exception.CommunityMatchUncertainException;
import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.repository.CommunityMembershipRepository;
import com.developer.copilot.community.repository.CommunityRepository;
import com.developer.copilot.community.repository.CommunityUrlAliasRepository;
import com.developer.copilot.community.service.CommunityAccountService;
import com.developer.copilot.community.service.CommunityCreationService;
import com.developer.copilot.community.service.CommunityCreationWriter;
import com.developer.copilot.community.service.CommunityDescriptionRefiner;
import com.developer.copilot.community.service.CommunityMessages;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.repository.JobRepository;

import com.developer.copilot.community.service.matching.CommunityNameGenerator;
import com.developer.copilot.community.service.matching.CommunityUrlMatcher;
import com.developer.copilot.community.service.matching.MatchDecision;
import com.developer.copilot.community.service.matching.NormalizedPosting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityCreationServiceImpl implements CommunityCreationService {

    private final CommunityAccountService communityAccountService;
    private final CurrentUserService currentUserService;
    private final JobRepository jobRepository;
    private final CommunityRepository communityRepository;
    private final CommunityUrlAliasRepository communityUrlAliasRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final CommunityUrlMatcher communityUrlMatcher;
    private final CommunityNameGenerator communityNameGenerator;
    private final CommunityCreationWriter communityCreationWriter;
    private final CommunityDescriptionRefiner communityDescriptionRefiner;
    private final CommunityAccountProperties communityAccountProperties;

    @Override
    @Transactional(readOnly = true)
    public CommunityPrefillResponse prefill(Long jobId) {
        communityAccountService.requireActiveAccount();
        User user = currentUserService.getCurrentUser();
        JobEntity job = ownedJob(jobId, user.getId());
        String description = sharedDescription(job);
        CommunityMatchResponse match;
        try {
            match = toMatchResponse(communityUrlMatcher.match(List.of(job.getSourceUrl())), user.getId());
        } catch (InvalidJobUrlException ex) {
            match = CommunityMatchResponse.builder()
                    .outcome(CommunityMatchOutcome.MATCH_UNCERTAIN)
                    .message(CommunityMessages.UNCERTAIN)
                    .build();
        }
        return CommunityPrefillResponse.builder()
                .jobId(job.getId())
                .title(job.getTitle())
                .company(job.getCompany())
                .location(job.getLocation())
                .description(description)
                .sourceUrl(job.getSourceUrl())
                .match(match)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityMatchResponse resolve(ResolveCommunityRequest request) {
        communityAccountService.requireActiveAccount();
        User user = currentUserService.getCurrentUser();
        return toMatchResponse(communityUrlMatcher.match(request.getUrls()), user.getId());
    }

    @Override
    public CommunityCreateResponse create(CreateCommunityRequest request) {
        communityAccountService.requireActiveAccount();
        User user = currentUserService.getCurrentUser();
        ownedJob(request.getJobId(), user.getId());
        List<String> urls = postingUrls(request);
        if (request.getAlternativeUrls() != null
                && request.getAlternativeUrls().size() > communityAccountProperties.getMaxUrlAliases()) {
            throw new CommunityValidationException(
                    CommunityMessages.tooManyUrls(communityAccountProperties.getMaxUrlAliases()));
        }
        MatchDecision decision = communityUrlMatcher.match(urls);
        if (decision.kind() == MatchDecision.MatchKind.EXISTING) {
            log.info("Community already exists for userId={} jobId={} communityId={}",
                    user.getId(), request.getJobId(), decision.communityId());
            return CommunityCreateResponse.builder()
                    .outcome(CommunityMatchOutcome.EXISTING_COMMUNITY)
                    .message(CommunityMessages.EXISTING)
                    .descriptionRefined(false)
                    .community(summary(decision.communityId(), user.getId()))
                    .build();
        }
        if (decision.kind() == MatchDecision.MatchKind.UNCERTAIN) {
            throw new CommunityMatchUncertainException(CommunityMessages.UNCERTAIN);
        }
        if (decision.kind() == MatchDecision.MatchKind.CONFLICT) {
            throw new CommunityAliasConflictException(CommunityMessages.CONFLICT);
        }

        String description = request.getDescription().trim();
        boolean refined = false;
        if (Boolean.TRUE.equals(request.getRefineDescription())) {
            CommunityDescriptionRefiner.Refinement refinement = communityDescriptionRefiner.refine(description, user.getId());
            description = refinement.description();
            refined = refinement.refined();
        }
        String location = blankToNull(request.getLocation());
        String title = request.getTitle().trim();
        String company = request.getCompany().trim();
        NormalizedPosting primary = decision.postings().get(0);
        String name = communityNameGenerator.generate(title, company, primary.hash(), false);
        try {
            Community community = communityCreationWriter.create(
                    user, primary.hash(), name, title, company, location, description, decision.postings());
            log.info("Created community id={} for userId={} jobId={}", community.getId(), user.getId(), request.getJobId());
            return CommunityCreateResponse.builder()
                    .outcome(CommunityMatchOutcome.CREATED)
                    .message(CommunityMessages.CREATED)
                    .descriptionRefined(refined)
                    .community(summary(community.getId(), user.getId()))
                    .build();
        } catch (DataIntegrityViolationException ex) {
            if (isConstraint(ex, "uk_community_name")) {
                String suffixed = communityNameGenerator.generate(title, company, primary.hash(), true);
                try {
                    Community community = communityCreationWriter.create(
                            user, primary.hash(), suffixed, title, company, location, description, decision.postings());
                    log.info("Created community id={} for userId={} jobId={}", community.getId(), user.getId(), request.getJobId());
                    return CommunityCreateResponse.builder()
                            .outcome(CommunityMatchOutcome.CREATED)
                            .message(CommunityMessages.CREATED)
                            .descriptionRefined(refined)
                            .community(summary(community.getId(), user.getId()))
                            .build();
                } catch (DataIntegrityViolationException second) {
                    return existingAfterRace(second, decision, user.getId(), request.getJobId());
                }
            }
            return existingAfterRace(ex, decision, user.getId(), request.getJobId());
        }
    }

    @Override
    public CommunityDescriptionResponse refine(String description) {
        communityAccountService.requireActiveAccount();
        User user = currentUserService.getCurrentUser();
        CommunityDescriptionRefiner.Refinement refinement = communityDescriptionRefiner.refine(description.trim(), user.getId());
        return CommunityDescriptionResponse.builder()
                .description(refinement.description())
                .refined(refinement.refined())
                .build();
    }

    private CommunityCreateResponse existingAfterRace(
            DataIntegrityViolationException ex,
            MatchDecision decision,
            Long userId,
            Long jobId) {
        Long communityId = findExistingCommunityId(decision);
        if (communityId == null || !isPostingConstraint(ex)) {
            throw ex;
        }
        log.info("Community create lost a uniqueness race for userId={} jobId={} communityId={}",
                userId, jobId, communityId);
        return CommunityCreateResponse.builder()
                .outcome(CommunityMatchOutcome.EXISTING_COMMUNITY)
                .message(CommunityMessages.EXISTING)
                .descriptionRefined(false)
                .community(summary(communityId, userId))
                .build();
    }

    private Long findExistingCommunityId(MatchDecision decision) {
        for (NormalizedPosting posting : decision.postings()) {
            var alias = communityUrlAliasRepository.findByNormalizedUrlHash(posting.hash());
            if (alias.isPresent()) {
                return alias.get().getCommunity().getId();
            }
        }
        if (!decision.postings().isEmpty()) {
            return communityRepository.findByCanonicalPostingKey(decision.postings().get(0).hash())
                    .map(Community::getId)
                    .orElse(null);
        }
        return null;
    }

    private JobEntity ownedJob(Long jobId, Long userId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new JobNotFoundException("Job not found with id: " + jobId));
    }

    private static String sharedDescription(JobEntity job) {
        if (job.getDescription() != null && !job.getDescription().isBlank()) {
            return job.getDescription();
        }
        if (job.getOriginalDescription() != null) {
            return job.getOriginalDescription();
        }
        return "";
    }

    private List<String> postingUrls(CreateCommunityRequest request) {
        List<String> urls = new ArrayList<>();
        urls.add(request.getSourceUrl());
        if (request.getAlternativeUrls() != null) {
            urls.addAll(request.getAlternativeUrls());
        }
        return urls;
    }

    private CommunityMatchResponse toMatchResponse(MatchDecision decision, Long userId) {
        return switch (decision.kind()) {
            case NO_MATCH -> CommunityMatchResponse.builder()
                    .outcome(CommunityMatchOutcome.NO_COMMUNITY)
                    .message(CommunityMessages.NO_MATCH)
                    .build();
            case EXISTING -> CommunityMatchResponse.builder()
                    .outcome(CommunityMatchOutcome.EXISTING_COMMUNITY)
                    .message(CommunityMessages.EXISTING)
                    .community(summary(decision.communityId(), userId))
                    .build();
            case UNCERTAIN -> CommunityMatchResponse.builder()
                    .outcome(CommunityMatchOutcome.MATCH_UNCERTAIN)
                    .message(CommunityMessages.UNCERTAIN)
                    .build();
            case CONFLICT -> CommunityMatchResponse.builder()
                    .outcome(CommunityMatchOutcome.ALIAS_CONFLICT)
                    .message(CommunityMessages.CONFLICT)
                    .build();
        };
    }

    private CommunitySummaryResponse summary(Long communityId, Long userId) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalStateException("Community disappeared during matching."));
        CommunityMembership membership = communityMembershipRepository
                .findByCommunityIdAndUserId(communityId, userId)
                .orElse(null);
        boolean member = membership != null && membership.getMembershipStatus() == MembershipStatus.ACTIVE;
        long activeMembers = communityMembershipRepository.countByCommunityIdAndMembershipStatus(
                communityId, MembershipStatus.ACTIVE);
        return CommunitySummaryResponse.builder()
                .id(community.getId())
                .communityName(community.getCommunityName())
                .title(community.getTitle())
                .company(community.getCompany())
                .location(community.getLocation())
                .description(community.getDescription())
                .createdAt(community.getCreatedAt())
                .membership(member ? CommunityMembershipView.MEMBER : CommunityMembershipView.NOT_A_MEMBER)
                .activeMemberCount(activeMembers)
                .build();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isPostingConstraint(DataIntegrityViolationException ex) {
        return isConstraint(ex, "uk_community_canonical_posting_key")
                || isConstraint(ex, "uk_community_url_alias_hash")
                || isConstraint(ex, "uk_community_name")
                || isConstraint(ex, "uk_community_membership_community_user");
    }

    private static boolean isConstraint(DataIntegrityViolationException ex, String name) {
        StringBuilder text = new StringBuilder();
        Throwable current = ex;
        while (current != null && text.length() < 4000) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return text.toString().toLowerCase(Locale.ROOT).contains(name);
    }
}
