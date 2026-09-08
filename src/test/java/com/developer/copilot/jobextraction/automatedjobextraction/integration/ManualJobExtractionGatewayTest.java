package com.developer.copilot.jobextraction.automatedjobextraction.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.jobextraction.manualextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.manualextraction.service.JobExtractionService;

@ExtendWith(MockitoExtension.class)
class ManualJobExtractionGatewayTest {

    @Mock
    private JobExtractionService jobExtractionService;

    @Test
    void parseExtractedContent_forwardsCanonicalUrlAndText() {
        ManualJobExtractionGateway gateway = new ManualJobExtractionGateway(jobExtractionService);
        when(jobExtractionService.extractJobInfo(any())).thenReturn(
                JobExtractionResultResponse.builder().title("T").company("C").build());

        JobExtractionResultResponse result = gateway.parseExtractedContent(
                "https://example.com/jobs/1", "Job Title: T\nJob Description:\nBuild APIs.");

        assertEquals("T", result.getTitle());
        ArgumentCaptor<JobExtractionRequest> captor = ArgumentCaptor.forClass(JobExtractionRequest.class);
        verify(jobExtractionService).extractJobInfo(captor.capture());
        assertEquals("https://example.com/jobs/1", captor.getValue().getSourceUrl());
        assertEquals("Job Title: T\nJob Description:\nBuild APIs.", captor.getValue().getRawJobText());
    }
}
