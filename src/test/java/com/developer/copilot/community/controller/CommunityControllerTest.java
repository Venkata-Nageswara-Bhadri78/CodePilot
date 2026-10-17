package com.developer.copilot.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.community.dto.CommunityMatchOutcome;
import com.developer.copilot.community.dto.CommunityMembershipView;
import com.developer.copilot.community.dto.response.CommunityCreateResponse;
import com.developer.copilot.community.dto.response.CommunitySummaryResponse;
import com.developer.copilot.community.exception.CommunityCreationExceptionHandler;
import com.developer.copilot.community.service.CommunityCreationService;
import com.developer.copilot.community.service.CommunityMessages;
import com.developer.copilot.jobs.exception.JobNotFoundException;

@ExtendWith(MockitoExtension.class)
class CommunityControllerTest {

    @Mock
    private CommunityCreationService communityCreationService;

    @InjectMocks
    private CommunityController communityController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(communityController)
                .setControllerAdvice(new GlobalExceptionHandler(), new CommunityCreationExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void create_missingTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/community")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":12,"company":"Northwind","description":"Build APIs","sourceUrl":"https://jobs.example.com/openings/role"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: Title is required."));
        verify(communityCreationService, never()).create(any());
    }

    @Test
    void create_withUserId_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/community")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":12,"title":"Backend Engineer","company":"Northwind","description":"Build APIs","sourceUrl":"https://jobs.example.com/openings/role","userId":4}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CommunityMessages.UNKNOWN_FIELD));
        verify(communityCreationService, never()).create(any());
    }

    @Test
    void prefill_unknownJob_returns404() throws Exception {
        when(communityCreationService.prefill(99L))
                .thenThrow(new JobNotFoundException("Job not found with id: 99"));

        mockMvc.perform(get("/api/v1/community/jobs/99/prefill"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job not found with id: 99"));
    }

    @Test
    void create_returns201WithoutPrivateFields() throws Exception {
        when(communityCreationService.create(any())).thenReturn(CommunityCreateResponse.builder()
                .outcome(CommunityMatchOutcome.CREATED)
                .message(CommunityMessages.CREATED)
                .descriptionRefined(false)
                .community(CommunitySummaryResponse.builder()
                        .id(10L)
                        .communityName("Backend Engineer at Northwind")
                        .title("Backend Engineer")
                        .company("Northwind")
                        .description("Build APIs")
                        .membership(CommunityMembershipView.MEMBER)
                        .activeMemberCount(1)
                        .build())
                .build());

        mockMvc.perform(post("/api/v1/community")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":12,"title":"Backend Engineer","company":"Northwind","description":"Build APIs","sourceUrl":"https://jobs.example.com/openings/role"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.outcome").value("CREATED"))
                .andExpect(jsonPath("$.data.community.communityName").value("Backend Engineer at Northwind"))
                .andExpect(jsonPath("$.data.community.membership").value("MEMBER"))
                .andExpect(jsonPath("$.data.community.email").doesNotExist())
                .andExpect(jsonPath("$.data.community.userId").doesNotExist());
    }
}
