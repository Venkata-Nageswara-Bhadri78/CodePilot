package com.developer.copilot.community.service.matching;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.community.repository.CommunityUrlAliasRepository;

/**
 * Decides whether posting URLs already belong to one community.
 * Identity is the normalized URL hash. Title and company are never compared.
 * No outbound fetch is performed.
 */
@Component
public class CommunityUrlMatcher {

    private static final Set<String> GENERIC_SINGLE_SEGMENTS = Set.of(
            "jobs", "job", "search", "careers", "career", "openings", "listing", "listings");

    private final UrlNormalizationUtil urlNormalizationUtil;
    private final CommunityUrlAliasRepository communityUrlAliasRepository;

    public CommunityUrlMatcher(
            UrlNormalizationUtil urlNormalizationUtil,
            CommunityUrlAliasRepository communityUrlAliasRepository) {
        this.urlNormalizationUtil = urlNormalizationUtil;
        this.communityUrlAliasRepository = communityUrlAliasRepository;
    }

    public NormalizedPosting normalize(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        String normalized = urlNormalizationUtil.normalizeStrict(trimmed);
        return new NormalizedPosting(
                trimmed,
                safeOriginal(trimmed),
                normalized,
                urlNormalizationUtil.sha256Hex(normalized),
                specific(normalized));
    }

    public MatchDecision match(List<String> rawUrls) {
        LinkedHashMap<String, NormalizedPosting> unique = new LinkedHashMap<>();
        boolean anyUncertain = false;
        if (rawUrls != null) {
            for (String rawUrl : rawUrls) {
                NormalizedPosting posting = normalize(rawUrl);
                if (!posting.specific()) {
                    anyUncertain = true;
                }
                unique.putIfAbsent(posting.hash(), posting);
            }
        }
        List<NormalizedPosting> postings = List.copyOf(unique.values());

        Set<Long> communityIds = new LinkedHashSet<>();
        for (NormalizedPosting posting : postings) {
            if (!posting.specific()) {
                continue;
            }
            communityUrlAliasRepository.findByNormalizedUrlHash(posting.hash())
                    .ifPresent(alias -> communityIds.add(alias.getCommunity().getId()));
        }
        if (communityIds.size() > 1) {
            return MatchDecision.conflict(postings);
        }
        if (communityIds.size() == 1) {
            return MatchDecision.existing(communityIds.iterator().next(), postings);
        }
        if (anyUncertain || postings.isEmpty()) {
            return MatchDecision.uncertain(postings);
        }
        return MatchDecision.noMatch(postings);
    }

    /**
     * A bare host or a single generic listing segment is not a posting identity.
     * A remaining query (after tracking parameters are removed) or a deeper path is.
     */
    boolean specific(String normalizedUrl) {
        URI uri = URI.create(normalizedUrl);
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            return true;
        }
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return false;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }
        if (segments.isEmpty()) {
            return false;
        }
        return segments.size() > 1
                || !GENERIC_SINGLE_SEGMENTS.contains(segments.get(0).toLowerCase(Locale.ROOT));
    }

    private static String safeOriginal(String trimmed) {
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("://") && lower.substring(lower.indexOf("://") + 3).contains("@")) {
            return null;
        }
        if (lower.contains("access_token=") || lower.contains("token=")) {
            return null;
        }
        return trimmed;
    }
}
