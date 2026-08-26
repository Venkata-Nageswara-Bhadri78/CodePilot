package com.developer.copilot.user.service.parsing;

import com.developer.copilot.common.storage.service.FileStorageService;
import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.entity.ResumeParsingStatus;
import com.developer.copilot.user.exception.ResumeParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeParserTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ResumeTextExtractor resumeTextExtractor;

    @Mock
    private ResumeSectionParser resumeSectionParser;

    private ResumeParser resumeParser;
    private ResumeProperties resumeProperties;
    private Resume resume;

    @BeforeEach
    void setUp() {
        resumeProperties = new ResumeProperties();

        resumeParser = new ResumeParser(
                fileStorageService,
                resumeTextExtractor,
                resumeSectionParser,
                new ResumeSectionsCodec(new ObjectMapper()),
                resumeProperties);

        resume = Resume.builder()
                .id(7L)
                .storageKey("users/1/resumes/a.pdf")
                .active(true)
                .build();
    }

    @Test
    void parseWithRetry_success_populatesCompletedRecord() {
        stubDownload();
        when(resumeTextExtractor.extract(any()))
                .thenReturn(new ExtractedResumeText("Jane Doe\nSUMMARY\nEngineer.", 2, false));
        when(resumeSectionParser.parse(any())).thenReturn(ParsedResumeContent.builder()
                .sections(Map.of(ResumeSection.SUMMARY, "Engineer."))
                .candidateName("Jane Doe")
                .email("jane@example.com")
                .build());

        ResumeParsedData result = resumeParser.parseWithRetry(resume, null);

        assertEquals(ResumeParsingStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        assertEquals("Jane Doe", result.getCandidateName());
        assertEquals("jane@example.com", result.getEmail());
        assertEquals(2, result.getPageCount());
        assertEquals(26, result.getCharacterCount());
        assertEquals("v1", result.getParserVersion());
        assertNotNull(result.getParsedAt());
        assertNull(result.getLastError());
        assertTrue(result.getSectionsJson().contains("SUMMARY"));
    }

    @Test
    void parseWithRetry_allAttemptsFail_marksFailedAfterConfiguredAttempts() {
        stubDownload();
        when(resumeTextExtractor.extract(any()))
                .thenThrow(new ResumeParsingException("Resume contains no extractable text."));

        ResumeParsedData result = resumeParser.parseWithRetry(resume, null);

        assertEquals(ResumeParsingStatus.FAILED, result.getStatus());
        assertEquals(3, result.getAttemptCount());
        assertTrue(result.getLastError().contains("no extractable text"));
        assertNotNull(result.getParsedAt());
        verify(resumeTextExtractor, times(3)).extract(any());
        verify(resumeSectionParser, never()).parse(any());
    }

    @Test
    void parseWithRetry_succeedsOnLastAttempt() {
        stubDownload();
        when(resumeTextExtractor.extract(any()))
                .thenThrow(new ResumeParsingException("transient failure"))
                .thenThrow(new ResumeParsingException("transient failure"))
                .thenReturn(new ExtractedResumeText("Jane Doe", 1, false));
        when(resumeSectionParser.parse(any())).thenReturn(ParsedResumeContent.builder().build());

        ResumeParsedData result = resumeParser.parseWithRetry(resume, null);

        assertEquals(ResumeParsingStatus.COMPLETED, result.getStatus());
        assertEquals(3, result.getAttemptCount());
        assertNull(result.getLastError());
    }

    @Test
    void parseWithRetry_honoursConfiguredAttemptLimit() {
        resumeProperties.getParsing().setMaxAttempts(1);
        stubDownload();
        when(resumeTextExtractor.extract(any()))
                .thenThrow(new ResumeParsingException("broken"));

        ResumeParsedData result = resumeParser.parseWithRetry(resume, null);

        assertEquals(ResumeParsingStatus.FAILED, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        verify(resumeTextExtractor, times(1)).extract(any());
    }

    @Test
    void parseWithRetry_storageFailure_isRetriedAndRecorded() {
        when(fileStorageService.download(any())).thenThrow(new IllegalStateException("storage down"));

        ResumeParsedData result = resumeParser.parseWithRetry(resume, null);

        assertEquals(ResumeParsingStatus.FAILED, result.getStatus());
        assertEquals(3, result.getAttemptCount());
        assertTrue(result.getLastError().contains("storage down"));
    }

    @Test
    void parseWithRetry_updatesExistingRecordInPlace() {
        stubDownload();
        when(resumeTextExtractor.extract(any()))
                .thenReturn(new ExtractedResumeText("Jane Doe", 1, false));
        when(resumeSectionParser.parse(any())).thenReturn(ParsedResumeContent.builder().build());

        ResumeParsedData existing = ResumeParsedData.builder()
                .id(99L)
                .resume(resume)
                .status(ResumeParsingStatus.PENDING)
                .attemptCount(0)
                .lastError("previous failure")
                .build();

        ResumeParsedData result = resumeParser.parseWithRetry(resume, existing);

        assertEquals(99L, result.getId());
        assertEquals(ResumeParsingStatus.COMPLETED, result.getStatus());
        assertNull(result.getLastError());
    }

    @Test
    void newPendingRecord_startsAtZeroAttempts() {
        ResumeParsedData pending = resumeParser.newPendingRecord(resume);

        assertEquals(ResumeParsingStatus.PENDING, pending.getStatus());
        assertEquals(0, pending.getAttemptCount());
        assertEquals(resume, pending.getResume());
    }

    private void stubDownload() {
        when(fileStorageService.download("users/1/resumes/a.pdf"))
                .thenReturn(new ByteArrayResource("%PDF-1.4".getBytes()));
    }
}
