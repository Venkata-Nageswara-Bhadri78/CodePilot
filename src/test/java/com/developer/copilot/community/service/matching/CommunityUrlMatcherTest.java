package com.developer.copilot.community.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.common.exception.InvalidJobUrlException;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityUrlAlias;
import com.developer.copilot.community.repository.CommunityUrlAliasRepository;

@ExtendWith(MockitoExtension.class)
class CommunityUrlMatcherTest {

    @Mock
    private CommunityUrlAliasRepository communityUrlAliasRepository;

    private CommunityUrlMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new CommunityUrlMatcher(new UrlNormalizationUtil(), communityUrlAliasRepository);
    }

    @Test
    void trackingParametersAndHostCasingNormalizeToTheSamePosting() {
        NormalizedPosting decorated = matcher.normalize(
                "HTTPS://WWW.LinkedIn.com/jobs/view/123/?utm_source=share&utm_medium=email");
        NormalizedPosting plain = matcher.normalize("https://linkedin.com/jobs/view/123");

        assertEquals(plain.hash(), decorated.hash());
        assertEquals(plain.normalizedUrl(), decorated.normalizedUrl());
        assertTrue(plain.specific());
    }

    @Test
    void jobIdentifyingQueryParameterIsKept() {
        NormalizedPosting first = matcher.normalize("https://example.com/apply?jobId=55&utm_source=board");
        NormalizedPosting second = matcher.normalize("https://example.com/apply?jobId=56");

        assertTrue(first.normalizedUrl().contains("jobId=55"));
        assertFalse(first.normalizedUrl().contains("utm_source"));
        assertFalse(first.hash().equals(second.hash()));
    }

    @Test
    void differentPostingPathsAreNotTheSame() {
        assertFalse(matcher.normalize("https://jobs.example.com/view/12").hash()
                .equals(matcher.normalize("https://jobs.example.com/view/432").hash()));
    }

    @Test
    void bareHostAndGenericListingAreNotSpecific() {
        assertFalse(matcher.normalize("https://www.linkedin.com").specific());
        assertFalse(matcher.normalize("https://example.com/jobs").specific());
        assertTrue(matcher.normalize("https://example.com/jobs/backend-engineer").specific());
    }

    @Test
    void unsafeSchemeIsRejected() {
        assertThrows(InvalidJobUrlException.class, () -> matcher.normalize("javascript:alert(1)"));
    }

    @Test
    void knownAliasResolvesToThatCommunity() {
        String url = "https://jobs.example.com/openings/backend-engineer";
        NormalizedPosting posting = matcher.normalize(url);
        when(communityUrlAliasRepository.findByNormalizedUrlHash(posting.hash()))
                .thenReturn(Optional.of(alias(44L, posting.hash())));

        MatchDecision decision = matcher.match(java.util.List.of(url));

        assertEquals(MatchDecision.MatchKind.EXISTING, decision.kind());
        assertEquals(44L, decision.communityId());
    }

    @Test
    void unknownSpecificUrlIsANewPosting() {
        when(communityUrlAliasRepository.findByNormalizedUrlHash(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        MatchDecision decision = matcher.match(java.util.List.of("https://jobs.example.com/openings/backend-engineer"));

        assertEquals(MatchDecision.MatchKind.NO_MATCH, decision.kind());
    }

    @Test
    void homepageDoesNotBecomeANewCommunity() {
        MatchDecision decision = matcher.match(java.util.List.of("https://linkedin.com/"));

        assertEquals(MatchDecision.MatchKind.UNCERTAIN, decision.kind());
    }

    @Test
    void urlsBelongingToDifferentCommunitiesConflict() {
        NormalizedPosting first = matcher.normalize("https://jobs.example.com/openings/one");
        NormalizedPosting second = matcher.normalize("https://jobs.example.com/openings/two");
        when(communityUrlAliasRepository.findByNormalizedUrlHash(first.hash()))
                .thenReturn(Optional.of(alias(1L, first.hash())));
        when(communityUrlAliasRepository.findByNormalizedUrlHash(second.hash()))
                .thenReturn(Optional.of(alias(2L, second.hash())));

        MatchDecision decision = matcher.match(java.util.List.of(
                "https://jobs.example.com/openings/one",
                "https://jobs.example.com/openings/two"));

        assertEquals(MatchDecision.MatchKind.CONFLICT, decision.kind());
    }

    private static CommunityUrlAlias alias(Long communityId, String hash) {
        Community community = new Community();
        community.setId(communityId);
        CommunityUrlAlias alias = new CommunityUrlAlias();
        alias.setCommunity(community);
        alias.setNormalizedUrlHash(hash);
        return alias;
    }
}
