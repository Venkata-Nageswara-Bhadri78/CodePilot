package com.developer.copilot.community.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.repository.CommunityMembershipRepository;

import lombok.RequiredArgsConstructor;

/**
 * Writes one user's membership. Each method has its own transaction so a uniqueness
 * failure rolls back only the losing insert and the caller can read the winner.
 */
@Service
@RequiredArgsConstructor
public class CommunityMembershipWriter {

    static final String MEMBERSHIP_UNIQUE = "uk_community_membership_community_user";

    private final CommunityMembershipRepository communityMembershipRepository;

    public record JoinWrite(CommunityMembership membership, JoinKind kind) {
    }

    public enum JoinKind {
        JOINED,
        ALREADY_MEMBER,
        REJOINED
    }

    public record LeaveWrite(CommunityMembership membership, LeaveKind kind) {
    }

    public enum LeaveKind {
        LEFT,
        ALREADY_LEFT,
        NOT_A_MEMBER
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JoinWrite join(Community community, User user) {
        CommunityMembership existing = communityMembershipRepository
                .findByCommunityIdAndUserId(community.getId(), user.getId())
                .orElse(null);
        if (existing != null) {
            if (existing.getMembershipStatus() == MembershipStatus.ACTIVE) {
                return new JoinWrite(existing, JoinKind.ALREADY_MEMBER);
            }
            existing.setMembershipStatus(MembershipStatus.ACTIVE);
            existing.setJoinedAt(LocalDateTime.now());
            existing.setLeftAt(null);
            return new JoinWrite(communityMembershipRepository.saveAndFlush(existing), JoinKind.REJOINED);
        }
        CommunityMembership created = new CommunityMembership();
        created.setCommunity(community);
        created.setUser(user);
        created.setMembershipStatus(MembershipStatus.ACTIVE);
        created.setJoinedAt(LocalDateTime.now());
        created.setLeftAt(null);
        return new JoinWrite(communityMembershipRepository.saveAndFlush(created), JoinKind.JOINED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LeaveWrite leave(Long communityId, Long userId) {
        CommunityMembership existing = communityMembershipRepository
                .findByCommunityIdAndUserId(communityId, userId)
                .orElse(null);
        if (existing == null) {
            return new LeaveWrite(null, LeaveKind.NOT_A_MEMBER);
        }
        if (existing.getMembershipStatus() == MembershipStatus.LEFT) {
            return new LeaveWrite(existing, LeaveKind.ALREADY_LEFT);
        }
        existing.setMembershipStatus(MembershipStatus.LEFT);
        existing.setLeftAt(LocalDateTime.now());
        return new LeaveWrite(communityMembershipRepository.saveAndFlush(existing), LeaveKind.LEFT);
    }

    public static boolean isDuplicateMembership(DataIntegrityViolationException ex) {
        StringBuilder text = new StringBuilder();
        Throwable current = ex;
        while (current != null && text.length() < 4000) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return text.toString().toLowerCase(Locale.ROOT).contains(MEMBERSHIP_UNIQUE);
    }
}
