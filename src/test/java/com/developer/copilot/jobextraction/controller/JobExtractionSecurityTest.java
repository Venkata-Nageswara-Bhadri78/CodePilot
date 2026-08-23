package com.developer.copilot.jobextraction.controller;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.jobextraction.service.JobExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobExtractionController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class JobExtractionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobExtractionService jobExtractionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void parseJobInfo_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "rawJobText": "Full pasted job posting text."
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
