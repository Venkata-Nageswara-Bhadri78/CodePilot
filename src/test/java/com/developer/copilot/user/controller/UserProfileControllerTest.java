package com.developer.copilot.user.controller;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.user.dto.profile.UserProfileResponse;
import com.developer.copilot.user.dto.profilelink.ProfileLinkResponse;
import com.developer.copilot.user.exception.ProfileItemLimitExceededException;
import com.developer.copilot.user.exception.WorkExperienceNotFoundException;
import com.developer.copilot.user.service.UserProfileService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private UserProfileController userProfileController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(userProfileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createProfile_emptyBody_returns201() throws Exception {
        when(userProfileService.createProfile(any())).thenReturn(
                UserProfileResponse.builder()
                        .id(1L)
                        .fullName("Test User")
                        .email("test@example.com")
                        .workExperiences(List.of())
                        .educations(List.of())
                        .projects(List.of())
                        .additionalInformation(List.of())
                        .profileLinks(List.of())
                        .build());

        mockMvc.perform(post("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Test User"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    void createProfile_headlineTooLong_returns400() throws Exception {
        String headline = "h".repeat(301);

        mockMvc.perform(post("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"" + headline + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Headline")));
        verify(userProfileService, never()).createProfile(any());
    }

    @Test
    void addProfileLink_javascriptUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/profile/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("http")));
        verify(userProfileService, never()).addProfileLink(any());
    }

    @Test
    void addProject_dataUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectTitle": "Demo",
                                  "projectLink": "data:text/html,hi"
                                }
                                """))
                .andExpect(status().isBadRequest());
        verify(userProfileService, never()).addProject(any());
    }

    @Test
    void addProfileLink_https_returns201() throws Exception {
        when(userProfileService.addProfileLink(any())).thenReturn(
                ProfileLinkResponse.builder().id(3L).url("https://github.com/me").build());

        mockMvc.perform(post("/api/v1/users/profile/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/me\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value("https://github.com/me"));
    }

    @Test
    void addWorkExperience_missingCompany_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/profile/experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobTitle": "Dev",
                                  "startYear": 2020
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Company name")));
        verify(userProfileService, never()).addWorkExperience(any());
    }

    @Test
    void addWorkExperience_endYearBeforeStart_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/profile/experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Acme",
                                  "jobTitle": "Dev",
                                  "startYear": 2023,
                                  "endYear": 2019
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "End year must be greater than or equal to start year.")));
    }

    @Test
    void addEducation_startYearTooEarly_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/profile/educations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "institutionName": "MIT",
                                  "field": "CS",
                                  "startYear": 1899
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1900")));
    }

    @Test
    void updateWorkExperience_unknownId_returns404() throws Exception {
        when(userProfileService.updateWorkExperience(eq(99L), any()))
                .thenThrow(new WorkExperienceNotFoundException());

        mockMvc.perform(put("/api/v1/users/profile/experiences/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Acme",
                                  "jobTitle": "Dev",
                                  "startYear": 2020
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void addWorkExperience_atCap_returns400() throws Exception {
        when(userProfileService.addWorkExperience(any()))
                .thenThrow(new ProfileItemLimitExceededException("work experience", 20));

        mockMvc.perform(post("/api/v1/users/profile/experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Acme",
                                  "jobTitle": "Dev",
                                  "startYear": 2020
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Maximum of 20 work experience records allowed."));
    }
}
