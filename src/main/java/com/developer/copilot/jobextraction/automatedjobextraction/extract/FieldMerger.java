package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fills blank fields from a lower-priority source without overwriting authoritative values.
 */
public final class FieldMerger {

    private FieldMerger() {
    }

    public static ExtractedJobFields merge(ExtractedJobFields primary, ExtractedJobFields extra) {
        if (primary == null) {
            return extra == null ? new ExtractedJobFields() : extra;
        }
        if (extra == null) {
            return primary;
        }
        fill(primary::getTitle, primary::setTitle, extra.getTitle());
        fill(primary::getCompany, primary::setCompany, extra.getCompany());
        fill(primary::getLocation, primary::setLocation, extra.getLocation());
        fill(primary::getEmploymentType, primary::setEmploymentType, extra.getEmploymentType());
        fill(primary::getWorkMode, primary::setWorkMode, extra.getWorkMode());
        fill(primary::getExperience, primary::setExperience, extra.getExperience());
        fill(primary::getSalary, primary::setSalary, extra.getSalary());
        fill(primary::getDescription, primary::setDescription, extra.getDescription());
        fill(primary::getApplicationDeadline, primary::setApplicationDeadline, extra.getApplicationDeadline());
        fill(primary::getPostedDate, primary::setPostedDate, extra.getPostedDate());
        fill(primary::getApplicationUrl, primary::setApplicationUrl, extra.getApplicationUrl());
        fill(primary::getJobId, primary::setJobId, extra.getJobId());
        fill(primary::getDepartment, primary::setDepartment, extra.getDepartment());
        fill(primary::getSeniorityLevel, primary::setSeniorityLevel, extra.getSeniorityLevel());
        fill(primary::getSourceUrl, primary::setSourceUrl, extra.getSourceUrl());
        primary.setResponsibilities(mergeLists(primary.getResponsibilities(), extra.getResponsibilities()));
        primary.setRequirements(mergeLists(primary.getRequirements(), extra.getRequirements()));
        primary.setPreferredQualifications(
                mergeLists(primary.getPreferredQualifications(), extra.getPreferredQualifications()));
        primary.setSkills(mergeLists(primary.getSkills(), extra.getSkills()));
        primary.setEducation(mergeLists(primary.getEducation(), extra.getEducation()));
        primary.setCertifications(mergeLists(primary.getCertifications(), extra.getCertifications()));
        primary.setBenefits(mergeLists(primary.getBenefits(), extra.getBenefits()));
        if (extra.isFromStructuredData()) {
            primary.setFromStructuredData(true);
        }
        return primary;
    }

    private static void fill(Supplier<String> getter, Consumer<String> setter, String candidate) {
        if (isBlank(getter.get()) && !isBlank(candidate)) {
            setter.accept(candidate.trim());
        }
    }

    private static List<String> mergeLists(List<String> primary, List<String> extra) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        addAll(seen, primary);
        addAll(seen, extra);
        return new ArrayList<>(seen);
    }

    private static void addAll(LinkedHashSet<String> seen, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                seen.add(value.trim());
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
