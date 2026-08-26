package com.developer.copilot.user.service.parsing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits extracted resume text into canonical sections and pulls out contact details.
 * <p>
 * Resumes have no machine-readable structure, so detection is heuristic: a line is
 * treated as a heading only when it is short and matches a known heading alias
 * exactly. Anything before the first heading is the header block, which is where
 * contact details normally live.
 */
@Component
public class ResumeSectionParser {

    private static final int MAX_HEADING_LENGTH = 60;
    private static final int MAX_HEADING_WORDS = 6;
    private static final int MAX_NAME_WORDS = 5;
    private static final int MIN_PHONE_DIGITS = 10;
    private static final int MAX_PHONE_DIGITS = 15;

    private static final Pattern LABELLED_FIELD = Pattern.compile(
            "^(name|title|email|e-mail|phone|mobile|contact|location|address|linkedin|github)\\s*[:\\-]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    private static final Pattern PHONE = Pattern.compile(
            "\\+?\\d[\\d\\s().\\-]{8,18}\\d");

    private static final Pattern LINKEDIN = Pattern.compile(
            "(?:https?://)?(?:[a-z]{2,3}\\.)?linkedin\\.com/[A-Za-z0-9_/\\-%.]+",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GITHUB = Pattern.compile(
            "(?:https?://)?(?:www\\.)?github\\.com/[A-Za-z0-9_/\\-%.]+",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DECORATION_ONLY = Pattern.compile("^[\\s=\\-*_#~|.•·]+$");

    private static final Pattern HEADING_SPLIT = Pattern.compile("\\s*(?:&|/|\\band\\b)\\s*");

    public ParsedResumeContent parse(String resumeText) {

        if (resumeText == null || resumeText.isBlank()) {
            return ParsedResumeContent.builder().build();
        }

        List<String> headerLines = new ArrayList<>();
        Map<ResumeSection, StringBuilder> bodies = new EnumMap<>(ResumeSection.class);
        ResumeSection current = null;

        for (String rawLine : resumeText.split("\n")) {

            String line = rawLine.strip();

            if (line.isEmpty() || DECORATION_ONLY.matcher(line).matches()) {
                if (current != null) {
                    bodies.get(current).append('\n');
                } else if (!headerLines.isEmpty()) {
                    headerLines.add("");
                }
                continue;
            }

            Optional<ResumeSection> heading = detectHeading(line);
            if (heading.isPresent()) {
                current = heading.get();
                bodies.computeIfAbsent(current, key -> new StringBuilder());
                continue;
            }

            if (current == null) {
                headerLines.add(line);
            } else {
                bodies.get(current).append(line).append('\n');
            }
        }

        Map<ResumeSection, String> sections = new LinkedHashMap<>();

        String headerBlock = String.join("\n", headerLines).strip();
        if (!headerBlock.isEmpty()) {
            sections.put(ResumeSection.CONTACT, headerBlock);
        }

        for (ResumeSection section : ResumeSection.values()) {
            StringBuilder body = bodies.get(section);
            if (body == null) {
                continue;
            }
            String cleaned = body.toString().replaceAll("\n{3,}", "\n\n").strip();
            if (!cleaned.isEmpty()) {
                sections.merge(section, cleaned, (existing, added) -> existing + "\n\n" + added);
            }
        }

        return buildContent(sections, headerLines, resumeText);
    }

    private ParsedResumeContent buildContent(Map<ResumeSection, String> sections,
                                             List<String> headerLines,
                                             String fullText) {

        Map<String, String> labelled = extractLabelledFields(headerLines);
        String headerBlock = String.join("\n", headerLines);

        String email = firstNonBlank(
                labelled.get("email"),
                labelled.get("e-mail"),
                findFirst(EMAIL, headerBlock),
                findFirst(EMAIL, fullText));

        String phone = firstNonBlank(
                normalizePhone(labelled.get("phone")),
                normalizePhone(labelled.get("mobile")),
                normalizePhone(labelled.get("contact")),
                findPhone(headerBlock),
                findPhone(fullText));

        String linkedin = firstNonBlank(
                labelled.get("linkedin"),
                findFirst(LINKEDIN, headerBlock),
                findFirst(LINKEDIN, fullText));

        String github = firstNonBlank(
                labelled.get("github"),
                findFirst(GITHUB, headerBlock),
                findFirst(GITHUB, fullText));

        String name = firstNonBlank(labelled.get("name"), guessCandidateName(headerLines));

        String location = firstNonBlank(
                labelled.get("location"),
                labelled.get("address"),
                guessLocation(headerLines, name));

        return ParsedResumeContent.builder()
                .sections(sections)
                .candidateName(truncate(name, 255))
                .email(truncate(email, 255))
                .phone(truncate(phone, 255))
                .location(truncate(location, 255))
                .linkedinUrl(truncate(linkedin, 255))
                .githubUrl(truncate(github, 255))
                .build();
    }

    private Optional<ResumeSection> detectHeading(String line) {

        String normalized = normalizeHeadingCandidate(line);

        if (normalized.isEmpty()
                || normalized.length() > MAX_HEADING_LENGTH
                || countWords(normalized) > MAX_HEADING_WORDS) {
            return Optional.empty();
        }

        Optional<ResumeSection> direct = ResumeSection.match(normalized);
        if (direct.isPresent()) {
            return direct;
        }

        // Compound headings such as "EDUCATION & CERTIFICATIONS" resolve to their first known part.
        for (String part : HEADING_SPLIT.split(normalized)) {
            Optional<ResumeSection> match = ResumeSection.match(part.strip());
            if (match.isPresent()) {
                return match;
            }
        }

        return Optional.empty();
    }

    private String normalizeHeadingCandidate(String line) {
        return line
                .replaceAll("^[\\s=\\-*_#•·~|>]+", "")
                .replaceAll("[\\s=\\-*_#•·~|:.]+$", "")
                .replaceAll("[^\\p{L}\\p{N}\\s&/]", " ")
                .replaceAll("\\s+", " ")
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    private Map<String, String> extractLabelledFields(List<String> headerLines) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : headerLines) {
            Matcher matcher = LABELLED_FIELD.matcher(line.strip());
            if (matcher.matches()) {
                String key = matcher.group(1).toLowerCase(Locale.ROOT);
                fields.putIfAbsent(key, matcher.group(2).strip());
            }
        }
        return fields;
    }

    private String guessCandidateName(List<String> headerLines) {
        for (String line : headerLines) {
            String candidate = line.strip();
            if (candidate.isEmpty() || candidate.length() > MAX_HEADING_LENGTH) {
                continue;
            }
            if (candidate.contains("@") || candidate.contains(":")
                    || candidate.matches(".*\\d.*")
                    || candidate.toLowerCase(Locale.ROOT).contains("http")
                    || candidate.toLowerCase(Locale.ROOT).contains("www.")) {
                continue;
            }
            int words = countWords(candidate);
            if (words < 1 || words > MAX_NAME_WORDS) {
                continue;
            }
            if (candidate.replaceAll("[^\\p{L}]", "").length() < 2) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private String guessLocation(List<String> headerLines, String name) {
        for (String line : headerLines) {
            String candidate = line.strip();
            if (candidate.isEmpty() || candidate.equals(name) || !candidate.contains(",")) {
                continue;
            }
            if (candidate.contains("@") || candidate.contains(":")
                    || candidate.matches(".*\\d.*")
                    || candidate.toLowerCase(Locale.ROOT).contains("http")) {
                continue;
            }
            if (countWords(candidate) > MAX_HEADING_WORDS) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private String findPhone(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PHONE.matcher(text);
        while (matcher.find()) {
            String normalized = normalizePhone(matcher.group());
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    /**
     * Accepts a phone candidate only when its digit count is plausible, which keeps
     * dates and identifiers out of the phone field.
     */
    private String normalizePhone(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = candidate.strip();
        long digits = trimmed.chars().filter(Character::isDigit).count();
        if (digits < MIN_PHONE_DIGITS || digits > MAX_PHONE_DIGITS) {
            return null;
        }
        return trimmed;
    }

    private String findFirst(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private int countWords(String text) {
        String stripped = text.strip();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
