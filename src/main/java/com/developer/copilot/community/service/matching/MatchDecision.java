package com.developer.copilot.community.service.matching;

import java.util.List;

public record MatchDecision(
        MatchKind kind,
        Long communityId,
        List<NormalizedPosting> postings) {

    public enum MatchKind {
        NO_MATCH,
        EXISTING,
        UNCERTAIN,
        CONFLICT
    }

    public static MatchDecision noMatch(List<NormalizedPosting> postings) {
        return new MatchDecision(MatchKind.NO_MATCH, null, postings);
    }

    public static MatchDecision existing(Long communityId, List<NormalizedPosting> postings) {
        return new MatchDecision(MatchKind.EXISTING, communityId, postings);
    }

    public static MatchDecision uncertain(List<NormalizedPosting> postings) {
        return new MatchDecision(MatchKind.UNCERTAIN, null, postings);
    }

    public static MatchDecision conflict(List<NormalizedPosting> postings) {
        return new MatchDecision(MatchKind.CONFLICT, null, postings);
    }
}
