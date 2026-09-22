package com.developer.copilot.jobs.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSkillsCollectionMigratorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private JobSkillsCollectionMigrator migrator;

    @Test
    void run_whenTableMissing_doesNothing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        migrator.run(new DefaultApplicationArguments());

        verify(jdbcTemplate, never()).queryForList(anyString());
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void run_copiesCommaSeparatedSkillsAndDropsTable() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.queryForList("SELECT job_id, skill FROM job_skills")).thenReturn(List.of(
                Map.of("job_id", 10L, "skill", "Java"),
                Map.of("job_id", 10L, "skill", "Spring Boot"),
                Map.of("job_id", 11L, "skill", "AWS")
        ));

        migrator.run(new DefaultApplicationArguments());

        verify(jdbcTemplate).update(
                "UPDATE jobs SET skills = ? WHERE id = ? AND (skills IS NULL OR skills = '')",
                "Java, Spring Boot",
                10L);
        verify(jdbcTemplate).update(
                "UPDATE jobs SET skills = ? WHERE id = ? AND (skills IS NULL OR skills = '')",
                "AWS",
                11L);
        verify(jdbcTemplate).execute("DROP TABLE IF EXISTS job_skills");
    }
}
