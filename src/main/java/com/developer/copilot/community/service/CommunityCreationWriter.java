package com.developer.copilot.community.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.CommunityUrlAlias;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.repository.CommunityMembershipRepository;
import com.developer.copilot.community.repository.CommunityRepository;
import com.developer.copilot.community.repository.CommunityUrlAliasRepository;
import com.developer.copilot.community.service.matching.NormalizedPosting;

import lombok.RequiredArgsConstructor;

/**
 * Inserts a community, its aliases, and the creator membership in one transaction.
 * A uniqueness failure rolls this unit back so a partial group is not left behind.
 */
@Service
@RequiredArgsConstructor
public class CommunityCreationWriter {

    private final CommunityRepository communityRepository;
    private final CommunityUrlAliasRepository communityUrlAliasRepository;
    private final CommunityMembershipRepository communityMembershipRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Community create(
            User creator,
            String canonicalPostingKey,
            String communityName,
            String title,
            String company,
            String location,
            String description,
            List<NormalizedPosting> postings) {
        Community community = new Community();
        community.setCanonicalPostingKey(canonicalPostingKey);
        community.setCommunityName(communityName);
        community.setTitle(title);
        community.setCompany(company);
        community.setLocation(location);
        community.setDescription(description);
        community.setCreatedBy(creator);
        community = communityRepository.saveAndFlush(community);

        for (NormalizedPosting posting : postings) {
            CommunityUrlAlias alias = new CommunityUrlAlias();
            alias.setCommunity(community);
            alias.setOriginalUrl(posting.storedOriginalUrl());
            alias.setNormalizedUrl(posting.normalizedUrl());
            alias.setNormalizedUrlHash(posting.hash());
            communityUrlAliasRepository.save(alias);
        }

        CommunityMembership membership = new CommunityMembership();
        membership.setCommunity(community);
        membership.setUser(creator);
        membership.setMembershipStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(LocalDateTime.now());
        communityMembershipRepository.saveAndFlush(membership);
        return community;
    }
}
