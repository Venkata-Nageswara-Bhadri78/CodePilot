package com.developer.copilot.jobs.mapper;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.JobSummaryResponse;
import com.developer.copilot.jobs.entity.JobEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                .skills("Java")
                .build();

        JobEntity entity = mapper.toEntity(request, user);

        assertNull(entity.getSourceUrl());
        assertNull(entity.getSourceUrlHash());
        assertEquals("SE", entity.getTitle());
        assertEquals(user, entity.getUser());
        assertEquals("", entity.getNotes());
        assertEquals(0, entity.getResumeToJobScore());
        assertEquals(com.developer.copilot.jobs.entity.JobStatus.APPLIED, entity.getJobStatus());
        assertNull(entity.getResume());
        assertEquals("Java", entity.getSkills());
    }

    @Test
    void toEntity_nullSkills_persistsEmptyString() {
        JobRequest request = JobRequest.builder()
                .title("SE")
                .company("Acme")
                .originalDescription("text")
                .skills(null)
                .build();

        JobEntity entity = mapper.toEntity(request, new User());
        assertEquals("", entity.getSkills());
    }

    @Test
    void updateEntityFromRequest_nullsAreNoOp() {
        mapper.updateEntityFromRequest(null, new JobRequest());
        mapper.updateEntityFromRequest(JobEntity.builder().skills("").build(), null);
    }

    @Test
    void updateEntityFromRequest_omittedSkills_clearsField() {
        JobEntity entity = JobEntity.builder()
                .skills("Java")
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
    void updateEntityFromPatch_omittedSkills_leavesField() {
        JobEntity entity = JobEntity.builder()
                .title("SE")
                .skills("Java")
                .build();

        mapper.updateEntityFromPatch(entity, JobPatchRequest.builder().salary("10 LPA").build());
        assertEquals("Java", entity.getSkills());
        assertEquals("10 LPA", entity.getSalary());
        assertEquals("SE", entity.getTitle());
    }

    @Test
    void updateEntityFromPatch_emptySkills_clearsField() {
        JobEntity entity = JobEntity.builder()
                .title("SE")
                .skills("Java, Spring Boot")
                .build();

        mapper.updateEntityFromPatch(entity, JobPatchRequest.builder().skills("").build());
        assertEquals("", entity.getSkills());
    }

    @Test
    void updateEntityFromPatch_notesApplied_putDoesNotClearNotes() {
        JobEntity entity = JobEntity.builder()
                .title("SE")
                .notes("keep me")
                .jobStatus(com.developer.copilot.jobs.entity.JobStatus.INTERVIEWS)
                .skills("Java")
                .build();

        mapper.updateEntityFromRequest(entity, JobRequest.builder()
                .title("SE")
                .company("Acme")
                .originalDescription("text")
                .build());

        assertEquals("keep me", entity.getNotes());
        assertEquals(com.developer.copilot.jobs.entity.JobStatus.INTERVIEWS, entity.getJobStatus());

        mapper.updateEntityFromPatch(entity, JobPatchRequest.builder().notes("updated").build());
        assertEquals("updated", entity.getNotes());
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
                .skills("Java")
                .build();

        JobSummaryResponse summary = mapper.toJobSummaryResponse(entity);
        JobResponse full = mapper.toJobResponse(entity);

        assertEquals("SE", summary.getTitle());
        assertEquals("Java", summary.getSkills());
        assertEquals("secret paste", full.getOriginalDescription());
        assertEquals("https://example.com/jobs/1", full.getSourceUrl());
    }

    @Test
    void toJobResponse_nullSkills_returnsEmptyString() {
        JobResponse response = mapper.toJobResponse(JobEntity.builder().title("SE").skills(null).build());
        assertEquals("", response.getSkills());
    }

    @Test
    void toJobResponse_nullEntity_returnsNull() {
        assertNull(mapper.toJobResponse(null));
        assertNull(mapper.toJobSummaryResponse(null));
    }
}
