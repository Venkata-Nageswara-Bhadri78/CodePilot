package com.developer.copilot.user.service.parsing;

import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.entity.ResumeParsingStatus;
import com.developer.copilot.user.repository.ResumeParsedDataRepository;
import com.developer.copilot.user.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeParsedDataWriterTest {

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @InjectMocks
    private ResumeParsedDataWriter writer;

    private Resume resume;
    private ResumeParsedData snapshot;

    @BeforeEach
    void setUp() {
        resume = Resume.builder().id(5L).storageKey("key").active(true).build();

        snapshot = ResumeParsedData.builder()
                .resume(resume)
                .status(ResumeParsingStatus.COMPLETED)
                .attemptCount(2)
                .rawText("Jane Doe")
                .candidateName("Jane Doe")
                .sectionsJson("{\"SUMMARY\":\"Engineer.\"}")
                .build();
    }

    @Test
    void persist_noExistingRecord_insertsNewOne() {
        when(resumeRepository.findById(5L)).thenReturn(Optional.of(resume));
        when(resumeParsedDataRepository.findByResumeId(5L)).thenReturn(Optional.empty());

        writer.persist(5L, snapshot);

        verify(resumeParsedDataRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getResume() == resume
                        && saved.getStatus() == ResumeParsingStatus.COMPLETED
                        && "Jane Doe".equals(saved.getCandidateName())));
    }

    @Test
    void persist_existingRecord_isUpdatedInPlace() {
        ResumeParsedData existing = ResumeParsedData.builder()
                .id(42L)
                .resume(resume)
                .status(ResumeParsingStatus.PENDING)
                .attemptCount(0)
                .lastError("earlier failure")
                .build();

        when(resumeRepository.findById(5L)).thenReturn(Optional.of(resume));
        when(resumeParsedDataRepository.findByResumeId(5L)).thenReturn(Optional.of(existing));

        writer.persist(5L, snapshot);

        assertEquals(ResumeParsingStatus.COMPLETED, existing.getStatus());
        assertEquals(2, existing.getAttemptCount());
        assertEquals("Jane Doe", existing.getCandidateName());
        assertEquals(42L, existing.getId());
        verify(resumeParsedDataRepository).saveAndFlush(existing);
    }

    @Test
    void persist_deletedResume_isSkipped() {
        when(resumeRepository.findById(5L)).thenReturn(Optional.empty());

        writer.persist(5L, snapshot);

        verify(resumeParsedDataRepository, never()).saveAndFlush(any());
    }
}
