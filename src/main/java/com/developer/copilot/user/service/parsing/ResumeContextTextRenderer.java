package com.developer.copilot.user.service.parsing;

import com.developer.copilot.user.entity.ResumeParsedData;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders a parsed resume as the structured plain text the AI prompt layer consumes.
 * <p>
 * The layout intentionally mirrors the existing candidate profile template so the
 * output is a drop-in replacement for the current static resume context.
 */
@Component
public class ResumeContextTextRenderer {

    private static final String MAJOR_RULE = "=".repeat(80);
    private static final String MINOR_RULE = "-".repeat(80);

    public String render(ResumeParsedData parsedData, Map<String, String> sections) {

        if (parsedData == null || !parsedData.isCompleted()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(MAJOR_RULE).append('\n')
                .append("CANDIDATE RESUME PROFILE").append('\n')
                .append(MAJOR_RULE).append('\n');

        appendField(sb, "NAME", parsedData.getCandidateName());
        appendField(sb, "LOCATION", parsedData.getLocation());
        appendField(sb, "EMAIL", parsedData.getEmail());
        appendField(sb, "PHONE", parsedData.getPhone());
        appendField(sb, "LINKEDIN", parsedData.getLinkedinUrl());
        appendField(sb, "GITHUB", parsedData.getGithubUrl());

        boolean anySection = false;

        for (ResumeSection section : ResumeSection.values()) {

            String body = sections == null ? null : sections.get(section.name());
            if (!StringUtils.hasText(body)) {
                continue;
            }

            if (section == ResumeSection.CONTACT) {
                body = stripAlreadyRenderedContactLines(body, parsedData);
                if (!StringUtils.hasText(body)) {
                    continue;
                }
            }

            sb.append('\n')
                    .append(MINOR_RULE).append('\n')
                    .append(section.getDisplayName()).append('\n')
                    .append(MINOR_RULE).append('\n')
                    .append(body.strip()).append('\n');

            anySection = true;
        }

        if (!anySection) {
            if (!StringUtils.hasText(parsedData.getRawText())) {
                return null;
            }
            sb.append('\n')
                    .append(MINOR_RULE).append('\n')
                    .append("RESUME CONTENT").append('\n')
                    .append(MINOR_RULE).append('\n')
                    .append(parsedData.getRawText().strip()).append('\n');
        }

        sb.append(MAJOR_RULE);

        return sb.toString();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (StringUtils.hasText(value)) {
            sb.append(label).append(": ").append(value.strip()).append('\n');
        }
    }

    /**
     * The header block usually repeats the name, email and links that are already
     * rendered as explicit fields. Dropping those lines keeps whatever else it held
     * (typically the candidate's headline) without duplicating the prompt.
     */
    private String stripAlreadyRenderedContactLines(String contactBlock, ResumeParsedData parsedData) {

        Set<String> rendered = new LinkedHashSet<>();
        addComparable(rendered, parsedData.getCandidateName());
        addComparable(rendered, parsedData.getLocation());
        addComparable(rendered, parsedData.getEmail());
        addComparable(rendered, parsedData.getPhone());
        addComparable(rendered, parsedData.getLinkedinUrl());
        addComparable(rendered, parsedData.getGithubUrl());

        List<String> kept = new ArrayList<>();

        for (String line : contactBlock.split("\n")) {
            String comparable = comparable(line);
            if (comparable.isEmpty() || rendered.contains(comparable)) {
                continue;
            }
            if (rendered.stream().anyMatch(comparable::contains)) {
                continue;
            }
            kept.add(line.strip());
        }

        return String.join("\n", kept).strip();
    }

    private void addComparable(Set<String> target, String value) {
        String comparable = comparable(value);
        if (!comparable.isEmpty()) {
            target.add(comparable);
        }
    }

    private String comparable(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
}
