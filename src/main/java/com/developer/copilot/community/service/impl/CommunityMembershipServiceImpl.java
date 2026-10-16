package com.developer.copilot.community.service.impl;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.community.dto.CommunityMembershipOutcome;
import com.developer.copilot.community.dto.CommunityMembershipView;
import com.developer.copilot.community.dto.response.CommunityListResponse;
import com.developer.copilot.community.dto.response.CommunityMemberResponse;
import com.developer.copilot.community.dto.response.CommunityMembersResponse;
import com.developer.copilot.community.dto.response.CommunityMembershipResponse;
import com.developer.copilot.community.dto.response.CommunitySummaryResponse;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.exception.CommunityMembershipRequiredException;
import com.developer.copilot.community.exception.CommunityUnavailableException;
import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.repository.CommunityMembershipRepository;
import com.developer.copilot.community.repository.CommunityRepository;
import com.developer.copilot.community.service.CommunityAccountService;
import com.developer.copilot.community.service.CommunityMembershipChanged;
import com.developer.copilot.community.service.CommunityMembershipService;
import com.developer.copilot.community.service.CommunityMembershipWriter;
import com.developer.copilot.community.service.CommunityMembershipWriter.JoinKind;
import com.developer.copilot.community.service.CommunityMembershipWriter.JoinWrite;
import com.developer.copilot.community.service.CommunityMembershipWriter.LeaveKind;
import com.developer.copilot.community.service.CommunityMembershipWriter.LeaveWrite;
import com.developer.copilot.community.service.CommunityMessages;
import com.developer.copilot.community.service.CommunitySummaryMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityMembershipServiceImpl implements CommunityMembershipService {

    private final CommunityAccountService communityAccountService;
    private final CurrentUserService currentUserService;
    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final CommunityMembershipWriter communityMembershipWriter;
    private final CommunitySummaryMapper communitySummaryMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public CommunityListResponse listMine() {
        communityAccountService.requireActiveAccount();
        User user = currentUserService.getCurrentUser();
        List<CommunityMembership> memberships = communityMembershipRepository
                .findByUserIdAndMembershipStatusOrderByJoinedAtDescIdDesc(user.getId(), MembershipStatus.ACTIVE);
        List<CommunitySummaryResponse> communities = memberships.stream()
                .map(membership -> communitySummaryMapper.toSummary(
                        membership.getCommunity(),
                        membership,
                        activeCount(membership.getCommunity().getId())))
                .toList();
        return CommunityListResponse.builder().communities(communities).build();
    }

    @Override
    @Transactional(readOnly = true)
    public CommunitySummaryResponse details(Long communityId) {
        CommunityMembership membership = requireActiveMembership(communityId);
        return communitySummaryMapper.toSummary(
                membership.getCommunity(),
                membership,
                activeCount(communityId));
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityMembersResponse members(Long communityId) {
        requireActiveMembership(communityId);
        List<CommunityMembership> memberships = communityMembershipRepository
                .findByCommunityIdAndMembershipStatusOrderByJoinedAtAscIdAsc(
                        communityId, MembershipStatus.ACTIVE);
        List<CommunityMemberResponse> members = memberships.stream()
                .map(CommunityMembershipServiceImpl::toMember)
                .toList();
        return CommunityMembersResponse.builder()
                .communityId(communityId)
                .activeMemberCount(members.size())
                .members(members)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityMembershipResponse status(Long communityId) {
        communityAccountService.requireActiveAccount();
        User user = currentUserService.getCurrentUser();
        Community community = requireCommunity(communityId);
        CommunityMembership membership = communityMembershipRepository
                .findByCommunityIdAndUserId(communityId, user.getId())
                .orElse(null);
        return toResponse(statusOutcome(membership), membership, community, user.getId());
    }

    @Override
    public CommunityMembershipResponse join(Long communityId) {
        communityAccountService.requireActiveAccount();
        User user = currentUserService.getCurrentUser();
        Community community = requireCommunity(communityId);
        try {
            JoinWrite result = communityMembershipWriter.join(community, user);
            log.info("Community join outcome={} communityId={} userId={}",
                    result.kind(), communityId, user.getId());
            publishJoin(communityId, user.getId(), result.kind());
            return toResponse(joinOutcome(result.kind()), result.membership(), community, user.getId());
        } catch (DataIntegrityViolationException ex) {
            if (!CommunityMembershipWriter.isDuplicateMembership(ex)) {
                throw ex;
            }
            CommunityMembership existing = communityMembershipRepository
                    .findByCommunityIdAndUserId(communityId, user.getId())
                    .orElse(null);
            if (existing == null) {
                throw ex;
            }
            if (existing.getMembershipStatus() != MembershipStatus.ACTIVE) {
                JoinWrite retried = communityMembershipWriter.join(community, user);
                log.info("Community join retried after a uniqueness race outcome={} communityId={} userId={}",
                        retried.kind(), communityId, user.getId());
                publishJoin(communityId, user.getId(), retried.kind());
                return toResponse(joinOutcome(retried.kind()), retried.membership(), community, user.getId());
            }
            log.info("Community join lost a uniqueness race communityId={} userId={}", communityId, user.getId());
            return toResponse(CommunityMembershipOutcome.ALREADY_MEMBER, existing, community, user.getId());
        }
    }

    @Override
    public CommunityMembershipResponse leave(Long communityId) {
        communityAccountService.requireActiveAccount();
        User user = currentUserService.getCurrentUser();
        Community community = requireCommunity(communityId);
        LeaveWrite result = communityMembershipWriter.leave(communityId, user.getId());
        log.info("Community leave outcome={} communityId={} userId={}",
                result.kind(), communityId, user.getId());
        if (result.kind() == LeaveKind.LEFT) {
            eventPublisher.publishEvent(new CommunityMembershipChanged(
                    communityId, user.getId(), MembershipStatus.LEFT));
        }
        return toResponse(leaveOutcome(result.kind()), result.membership(), community, user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityMembership requireActiveMembership(Long communityId) {
        communityAccountService.requireActiveAccount();
        User user = currentUserService.getCurrentUser();
        requireCommunity(communityId);
        CommunityMembership membership = communityMembershipRepository
                .findByCommunityIdAndUserId(communityId, user.getId())
                .orElseThrow(() -> new CommunityMembershipRequiredException(CommunityMessages.MEMBERSHIP_REQUIRED));
        if (membership.getMembershipStatus() != MembershipStatus.ACTIVE) {
            throw new CommunityMembershipRequiredException(CommunityMessages.MEMBERSHIP_REQUIRED);
        }
        return membership;
    }

    private void publishJoin(Long communityId, Long userId, JoinKind kind) {
        if (kind == JoinKind.JOINED || kind == JoinKind.REJOINED) {
            eventPublisher.publishEvent(new CommunityMembershipChanged(
                    communityId, userId, MembershipStatus.ACTIVE));
        }
    }

    private Community requireCommunity(Long communityId) {
        if (communityId == null || communityId <= 0) {
            throw new CommunityValidationException(CommunityMessages.INVALID_COMMUNITY_ID);
        }
        return communityRepository.findWithCreatorById(communityId)
                .orElseThrow(() -> new CommunityUnavailableException(CommunityMessages.COMMUNITY_UNAVAILABLE));
    }

    private long activeCount(Long communityId) {
        return communityMembershipRepository.countByCommunityIdAndMembershipStatus(
                communityId, MembershipStatus.ACTIVE);
    }

    private CommunityMembershipResponse toResponse(
            CommunityMembershipOutcome outcome,
            CommunityMembership membership,
            Community community,
            Long userId) {
        boolean active = membership != null && membership.getMembershipStatus() == MembershipStatus.ACTIVE;
        boolean creator = community.getCreatedBy() != null && userId.equals(community.getCreatedBy().getId());
        return CommunityMembershipResponse.builder()
                .outcome(outcome)
                .message(outcome.message())
                .communityId(community.getId())
                .membership(active ? CommunityMembershipView.MEMBER : CommunityMembershipView.NOT_A_MEMBER)
                .membershipStatus(membership == null ? null : membership.getMembershipStatus())
                .joinedAt(membership == null ? null : membership.getJoinedAt())
                .leftAt(membership == null ? null : membership.getLeftAt())
                .creator(creator)
                .activeMemberCount(activeCount(community.getId()))
                .build();
    }

    private static CommunityMemberResponse toMember(CommunityMembership membership) {
        User user = membership.getUser();
        return CommunityMemberResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .membershipStatus(membership.getMembershipStatus())
                .joinedAt(membership.getJoinedAt())
                .build();
    }

    private static CommunityMembershipOutcome statusOutcome(CommunityMembership membership) {
        if (membership == null) {
            return CommunityMembershipOutcome.NOT_A_MEMBER;
        }
        if (membership.getMembershipStatus() == MembershipStatus.ACTIVE) {
            return CommunityMembershipOutcome.MEMBER;
        }
        return CommunityMembershipOutcome.ALREADY_LEFT;
    }

    private static CommunityMembershipOutcome joinOutcome(JoinKind kind) {
        return switch (kind) {
            case JOINED -> CommunityMembershipOutcome.JOINED;
            case ALREADY_MEMBER -> CommunityMembershipOutcome.ALREADY_MEMBER;
            case REJOINED -> CommunityMembershipOutcome.REJOINED;
        };
    }

    private static CommunityMembershipOutcome leaveOutcome(LeaveKind kind) {
        return switch (kind) {
            case LEFT -> CommunityMembershipOutcome.LEFT;
            case ALREADY_LEFT -> CommunityMembershipOutcome.ALREADY_LEFT;
            case NOT_A_MEMBER -> CommunityMembershipOutcome.NOT_A_MEMBER;
        };
    }
}
