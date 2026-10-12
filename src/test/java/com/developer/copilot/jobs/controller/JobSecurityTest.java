package com.developer.copilot.jobs.controller;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.jobs.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class JobSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void getAllJobs_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isUnauthorized());
        verify(jobService, never()).getAllJobs(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createJob_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verify(jobService, never()).createJob(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createJob_extensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
        verify(jobService, never()).createJob(any());
    }

    @Test
    void getAllJobs_webClient_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").with(user("john@example.com").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void updateJob_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchJob_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteJob_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/jobs/1"))
                .andExpect(status().isUnauthorized());
        verify(jobService, never()).deleteJob(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateLocation_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/jobs/1/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"Remote\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateResume_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/jobs/1/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resume\":12}"))
                .andExpect(status().isUnauthorized());
        verify(jobService, never()).updateResume(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateNotes_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/jobs/1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateJobStatus_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/jobs/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobStatus\":\"INTERVIEWS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageBearerToken_returns401_andDoesNotCallService() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        verify(jobService, never()).getAllJobs(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static RequestPostProcessor extensionUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}
