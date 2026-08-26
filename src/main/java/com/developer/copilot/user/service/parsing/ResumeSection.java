package com.developer.copilot.user.service.parsing;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical resume sections and the headings commonly used for them.
 * <p>
 * Declaration order is also the render order used when the parsed resume is turned
 * back into prompt text.
 */
public enum ResumeSection {

    CONTACT("CONTACT INFORMATION",
            "contact", "contact information", "contact details", "personal details"),

    SUMMARY("PROFESSIONAL SUMMARY",
            "summary", "professional summary", "profile", "professional profile",
            "objective", "career objective", "about", "about me", "overview"),

    SKILLS("CORE TECHNICAL SKILLS",
            "skills", "technical skills", "core skills", "core competencies",
            "competencies", "technologies", "technical expertise", "skill set",
            "tech stack", "areas of expertise", "expertise"),

    EXPERIENCE("PROFESSIONAL WORK EXPERIENCE",
            "experience", "work experience", "professional experience", "employment",
            "employment history", "work history", "career history",
            "professional background", "relevant experience"),

    PROJECTS("PROJECTS",
            "projects", "featured projects", "personal projects", "academic projects",
            "key projects", "selected projects", "notable projects"),

    EDUCATION("EDUCATION",
            "education", "academics", "academic background", "academic qualifications",
            "educational background", "qualifications"),

    CERTIFICATIONS("CERTIFICATIONS",
            "certification", "certifications", "certificates", "licenses",
            "licenses and certifications", "courses", "training"),

    ACHIEVEMENTS("ACHIEVEMENTS",
            "achievements", "awards", "honors", "honours", "awards and honors",
            "accomplishments"),

    PUBLICATIONS("PUBLICATIONS",
            "publications", "research", "papers", "patents"),

    LANGUAGES("LANGUAGES",
            "languages", "language proficiency", "languages known"),

    INTERESTS("INTERESTS",
            "interests", "hobbies", "activities", "extracurricular activities"),

    ADDITIONAL("ADDITIONAL INFORMATION",
            "additional information", "other", "miscellaneous", "references", "volunteering");

    private final String displayName;
    private final Set<String> aliases;

    ResumeSection(String displayName, String... aliases) {
        this.displayName = displayName;
        this.aliases = new LinkedHashSet<>(Arrays.asList(aliases));
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Matches an already-normalized heading candidate (lowercase, stripped of
     * decoration and punctuation) against the known aliases.
     */
    public static Optional<ResumeSection> match(String normalizedHeading) {
        if (normalizedHeading == null || normalizedHeading.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(section -> section.aliases.contains(normalizedHeading))
                .findFirst();
    }
}
