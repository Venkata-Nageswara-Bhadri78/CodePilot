package com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractedJobFields;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractionSupport;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.JobExtractionStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Schema.org JobPosting embedded as JSON-LD. Highest-priority structured signal.
 */
@Slf4j
@Component
@Order(0)
public class JsonLdJobExtractionStrategy implements JobExtractionStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int order() {
        return 0;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        return original != null && !original.select("script[type=application/ld+json]").isEmpty();
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        ExtractedJobFields fields = new ExtractedJobFields();
        if (original == null) {
            return fields;
        }
        List<JsonNode> postings = new ArrayList<>();
        for (Element script : original.select("script[type=application/ld+json]")) {
            collectJobPostings(script.data(), postings);
        }
        JsonNode chosen = choosePosting(url, postings);
        if (chosen == null) {
            return fields;
        }
        fields.setFromStructuredData(true);
        fields.setTitle(text(chosen, "title"));
        if (fields.getTitle() == null) {
            fields.setTitle(text(chosen, "name"));
        }
        fields.setDescription(ExtractionSupport.htmlToText(text(chosen, "description")));
        fields.setEmploymentType(joinOrText(chosen.get("employmentType")));
        fields.setPostedDate(text(chosen, "datePosted"));
        fields.setApplicationDeadline(firstNonBlank(text(chosen, "validThrough"), text(chosen, "applicationDeadline")));
        fields.setApplicationUrl(firstNonBlank(text(chosen, "url"), text(chosen, "directApply")));
        fields.setJobId(identifier(chosen.get("identifier")));
        fields.setCompany(organizationName(chosen.get("hiringOrganization")));
        fields.setLocation(jobLocation(chosen.get("jobLocation")));
        fields.setSalary(salary(chosen.get("baseSalary")));
        fields.setExperience(firstNonBlank(
                text(chosen, "experienceRequirements"),
                text(chosen.path("experienceRequirements"), "monthsOfExperience")));
        fields.setDepartment(text(chosen, "occupationalCategory"));
        fields.setWorkMode(workMode(chosen));
        addIfPresent(ensureList(fields.getSkills(), fields::setSkills), splitMaybe(text(chosen, "skills")));
        addIfPresent(ensureList(fields.getEducation(), fields::setEducation),
                splitMaybe(education(chosen.get("educationRequirements"))));
        addIfPresent(ensureList(fields.getResponsibilities(), fields::setResponsibilities),
                splitMaybe(text(chosen, "responsibilities")));
        addIfPresent(ensureList(fields.getBenefits(), fields::setBenefits),
                splitMaybe(text(chosen, "jobBenefits")));
        addIfPresent(ensureList(fields.getRequirements(), fields::setRequirements),
                splitMaybe(text(chosen, "qualifications")));
        if (url != null) {
            fields.setSourceUrl(url.toString());
        }
        return fields;
    }

    private void collectJobPostings(String raw, List<JsonNode> sink) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(raw.trim());
            walk(root, sink);
        } catch (Exception ex) {
            log.debug("Ignoring unparseable JSON-LD block");
        }
    }

    private void walk(JsonNode node, List<JsonNode> sink) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> walk(child, sink));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (isJobPosting(node.get("@type"))) {
            sink.add(node);
        }
        if (node.has("@graph")) {
            walk(node.get("@graph"), sink);
        }
        node.fields().forEachRemaining(entry -> {
            if (!"@graph".equals(entry.getKey()) && entry.getValue() != null && entry.getValue().isContainerNode()) {
                if (!isJobPosting(node.get("@type"))) {
                    walk(entry.getValue(), sink);
                }
            }
        });
    }

    private static boolean isJobPosting(JsonNode typeNode) {
        if (typeNode == null || typeNode.isNull()) {
            return false;
        }
        if (typeNode.isArray()) {
            for (JsonNode n : typeNode) {
                if (isJobPostingName(n.asText())) {
                    return true;
                }
            }
            return false;
        }
        return isJobPostingName(typeNode.asText());
    }

    private static boolean isJobPostingName(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("jobposting");
    }

    private JsonNode choosePosting(URI url, List<JsonNode> postings) {
        if (postings.isEmpty()) {
            return null;
        }
        if (postings.size() == 1) {
            return postings.get(0);
        }
        String urlString = url == null ? "" : url.toString().toLowerCase(Locale.ROOT);
        String path = url == null || url.getPath() == null ? "" : url.getPath().toLowerCase(Locale.ROOT);
        for (JsonNode posting : postings) {
            String postingUrl = text(posting, "url");
            String id = identifier(posting.get("identifier"));
            if (postingUrl != null && urlString.contains(postingUrl.toLowerCase(Locale.ROOT))) {
                return posting;
            }
            if (id != null && (urlString.contains(id.toLowerCase(Locale.ROOT))
                    || path.contains(id.toLowerCase(Locale.ROOT)))) {
                return posting;
            }
        }
        return postings.get(0);
    }

    private static String identifier(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber()) {
            return ExtractionSupport.cleanText(node.asText());
        }
        return firstNonBlank(text(node, "value"), text(node, "name"));
    }

    private static String organizationName(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return ExtractionSupport.cleanText(node.asText());
        }
        return text(node, "name");
    }

    private static String jobLocation(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray() && node.size() > 0) {
            return jobLocation(node.get(0));
        }
        if (node.isTextual()) {
            return ExtractionSupport.cleanText(node.asText());
        }
        JsonNode address = node.get("address");
        if (address != null && address.isObject()) {
            String locality = text(address, "addressLocality");
            String region = text(address, "addressRegion");
            String country = text(address, "addressCountry");
            StringBuilder sb = new StringBuilder();
            appendPart(sb, locality);
            appendPart(sb, region);
            appendPart(sb, country);
            return sb.length() == 0 ? text(node, "name") : sb.toString();
        }
        return firstNonBlank(text(node, "name"), text(node, "address"));
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part);
    }

    private static String salary(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber()) {
            return ExtractionSupport.cleanText(node.asText());
        }
        JsonNode value = node.get("value");
        String currency = text(node, "currency");
        if (value != null && value.isObject()) {
            String min = text(value, "minValue");
            String max = text(value, "maxValue");
            String unit = text(value, "unitText");
            StringBuilder sb = new StringBuilder();
            if (min != null && max != null) {
                sb.append(min).append('-').append(max);
            } else if (min != null) {
                sb.append(min);
            } else if (max != null) {
                sb.append(max);
            } else {
                String v = text(value, "value");
                if (v != null) {
                    sb.append(v);
                }
            }
            if (currency != null) {
                sb.append(' ').append(currency);
            }
            if (unit != null) {
                sb.append(" per ").append(unit);
            }
            return ExtractionSupport.cleanText(sb.toString());
        }
        return firstNonBlank(text(node, "value"), text(node, "name"));
    }

    private static String workMode(JsonNode chosen) {
        JsonNode locations = chosen.get("jobLocationType");
        String type = joinOrText(locations);
        if (type == null) {
            return null;
        }
        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.contains("telecommute") || lower.contains("remote")) {
            return "remote";
        }
        return ExtractionSupport.cleanText(type);
    }

    private static String education(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return firstNonBlank(text(node, "credentialCategory"), text(node, "name"), text(node, "description"));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        return joinOrText(value);
    }

    private static String joinOrText(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode n : value) {
                String part = ExtractionSupport.cleanText(n.asText());
                if (part != null) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(part);
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        if (value.isObject()) {
            return firstNonBlank(text(value, "name"), text(value, "value"), text(value, "description"));
        }
        return ExtractionSupport.cleanText(value.asText());
    }

    private static List<String> splitMaybe(String value) {
        List<String> items = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return items;
        }
        if (value.contains(",") && value.length() < 400) {
            for (String part : value.split(",")) {
                String cleaned = ExtractionSupport.cleanText(part);
                if (cleaned != null) {
                    items.add(cleaned);
                }
            }
            return items;
        }
        items.add(value.trim());
        return items;
    }

    private static List<String> ensureList(List<String> current, java.util.function.Consumer<List<String>> setter) {
        if (current != null) {
            return current;
        }
        List<String> created = new ArrayList<>();
        setter.accept(created);
        return created;
    }

    private static void addIfPresent(List<String> target, List<String> extra) {
        if (target == null || extra == null || extra.isEmpty()) {
            return;
        }
        target.addAll(extra);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
