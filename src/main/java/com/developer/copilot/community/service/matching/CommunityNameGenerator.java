package com.developer.copilot.community.service.matching;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.developer.copilot.community.repository.CommunityRepository;

/**
 * Readable community names from title and company. The canonical posting key is only
 * appended when the readable name is already taken, so two postings can share a title
 * without sharing a name. Callers cannot supply the name.
 */
@Component
public class CommunityNameGenerator {

    private final CommunityRepository communityRepository;

    public CommunityNameGenerator(CommunityRepository communityRepository) {
        this.communityRepository = communityRepository;
    }

    public String generate(String title, String company, String canonicalPostingKey, boolean forceSuffix) {
        String base = readable(title);
        String companyName = readable(company);
        String combined = companyName.isEmpty() ? base : base + " at " + companyName;
        if (combined.isBlank()) {
            combined = "Community";
        }
        String suffix = "-" + canonicalPostingKey.substring(0, 8).toLowerCase(Locale.ROOT);
        if (forceSuffix || communityRepository.existsByCommunityName(clip(combined, 255))) {
            return clip(combined, 255 - suffix.length()) + suffix;
        }
        return clip(combined, 255);
    }

    private static String readable(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\p{Cntrl}]", "").replaceAll("\\s+", " ").trim();
    }

    private static String clip(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max).trim();
    }
}
