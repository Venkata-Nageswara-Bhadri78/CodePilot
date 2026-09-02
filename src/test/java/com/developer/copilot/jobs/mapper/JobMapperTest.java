package com.developer.copilot.jobs.mapper;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.JobSummaryResponse;
import com.developer.copilot.jobs.entity.JobEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobMapperTest {

    private final JobMapper mapper = new JobMapper();

    @Test
    void toEntity_nullRequest_returnsNull() {
        assertNull(mapper.toEntity(null, new User()));
    }

    @Test
    void toEntity_doesNotSetSourceUrlOrHash() {
        User user = new User();
        user.setId(1L);
        JobRequest request = JobRequest.builder()
                .title("SE")
                .company("Acme")
                .sourceUrl("https://example.com/jobs/1")
                .originalDescription("text")
                .skills(new ArrayList<>(List.of("Java")))
                .build();

        JobEntity entity = mapper.toEntity(request, user);

        assertNull(entity.getSourceUrl());
        assertNull(entity.getSourceUrlHash());
        assertEquals("SE", entity.getTitle());
        assertEquals(user, entity.getUser());
    }

    @Test
    void toEntity_skillsCopiedIntoNewList() {
        List<String> skills = new ArrayList<>(List.of("Java"));
        JobRequest request = JobRequest.builder()
                .title("SE")
                .company("Acme")
                .originalDescription("text")
                .skills(skills)
                .build();

        JobEntity entity = mapper.toEntity(request, new User());
        skills.add("Go");

        assertEquals(List.of("Java"), entity.getSkills());
        assertNotSame(skills, entity.getSkills());
    }

    @Test
    void updateEntityFromRequest_nullsAreNoOp() {
        mapper.updateEntityFromRequest(null, new JobRequest());
        mapper.updateEntityFromRequest(JobEntity.builder().skills(new ArrayList<>()).build(), null);
    }

    @Test
    void updateEntityFromRequest_omittedSkills_clearsList() {
        JobEntity entity = JobEntity.builder()
                .skills(new ArrayList<>(List.of("Java")))
                .build();
        JobRequest request = JobRequest.builder()
                .title("SE")
                .company("Acme")
                .originalDescription("text")
                .skills(null)
                .build();

        mapper.updateEntityFromRequest(entity, request);
        assertTrue(entity.getSkills().isEmpty());
    }

    @Test
    void updateEntityFromPatch_omittedSkills_leavesList() {
        JobEntity entity = JobEntity.builder()
                .title("SE")
                .skills(new ArrayList<>(List.of("Java")))
                .build();

        mapper.updateEntityFromPatch(entity, JobPatchRequest.builder().salary("10 LPA").build());
        assertEquals(List.of("Java"), entity.getSkills());
        assertEquals("10 LPA", entity.getSalary());
        assertEquals("SE", entity.getTitle());
    }

    @Test
    void toJobSummaryResponse_omitsDescriptionsAndSourceUrl() {
        JobEntity entity = JobEntity.builder()
                .id(5L)
                .title("SE")
                .company("Acme")
                .sourceUrl("https://example.com/jobs/1")
                .originalDescription("secret paste")
                .description("cleaned")
                .skills(List.of("Java"))
                .build();

        JobSummaryResponse summary = mapper.toJobSummaryResponse(entity);
        JobResponse full = mapper.toJobResponse(entity);

        assertEquals("SE", summary.getTitle());
        assertEquals(List.of("Java"), summary.getSkills());
        assertEquals("secret paste", full.getOriginalDescription());
        assertEquals("https://example.com/jobs/1", full.getSourceUrl());
    }

    @Test
    void toJobResponse_nullEntity_returnsNull() {
        assertNull(mapper.toJobResponse(null));
        assertNull(mapper.toJobSummaryResponse(null));
    }
}
