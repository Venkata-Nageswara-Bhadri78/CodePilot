package com.developer.copilot.jobs.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time copy of legacy {@code job_skills} rows into {@code jobs.skills}, then drops
 * the collection table. Hibernate {@code ddl-auto=update} adds the new column but does
 * not move existing collection data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobSkillsCollectionMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!jobSkillsTableExists()) {
            return;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT job_id, skill FROM job_skills");
        Map<Long, List<String>> skillsByJob = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object jobIdValue = row.get("job_id");
            Object skillValue = row.get("skill");
            if (jobIdValue == null) {
                continue;
            }
            Long jobId = ((Number) jobIdValue).longValue();
            String skill = skillValue == null ? null : skillValue.toString();
            if (!StringUtils.hasText(skill)) {
                continue;
            }
            skillsByJob.computeIfAbsent(jobId, id -> new ArrayList<>()).add(skill.trim());
        }

        for (Map.Entry<Long, List<String>> entry : skillsByJob.entrySet()) {
            String joined = String.join(", ", entry.getValue());
            jdbcTemplate.update(
                    "UPDATE jobs SET skills = ? WHERE id = ? AND (skills IS NULL OR skills = '')",
                    joined,
                    entry.getKey());
        }

        jdbcTemplate.execute("DROP TABLE IF EXISTS job_skills");
        log.info("Migrated skills for {} jobs from job_skills into jobs.skills and dropped job_skills.",
                skillsByJob.size());
    }

    private boolean jobSkillsTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND LOWER(table_name) = 'job_skills'",
                Integer.class);
        return count != null && count > 0;
    }
}
