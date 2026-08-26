package com.developer.copilot.user.service.parsing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts detected resume sections to and from the JSON stored in
 * {@code resume_parsed_data.sections_json}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeSectionsCodec {

    private static final TypeReference<LinkedHashMap<String, String>> SECTION_MAP =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public String toJson(Map<ResumeSection, String> sections) {

        if (sections == null || sections.isEmpty()) {
            return null;
        }

        Map<String, String> serializable = new LinkedHashMap<>();
        sections.forEach((section, body) -> serializable.put(section.name(), body));

        try {
            return objectMapper.writeValueAsString(serializable);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to serialize parsed resume sections.", ex);
        }
    }

    /**
     * Never throws: a record with unreadable section JSON still has usable raw text,
     * so a decode failure degrades to an empty section map rather than an error.
     */
    public Map<String, String> fromJson(String json) {

        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }

        try {
            return objectMapper.readValue(json, SECTION_MAP);
        } catch (JacksonException ex) {
            log.warn("Unable to deserialize parsed resume sections, falling back to raw text", ex);
            return new LinkedHashMap<>();
        }
    }
}
