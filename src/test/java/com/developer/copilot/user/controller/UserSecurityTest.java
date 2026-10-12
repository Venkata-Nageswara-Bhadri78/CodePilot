package com.developer.copilot.user.controller;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.user.dto.ResumeResponse;
import com.developer.copilot.user.service.UserProfileService;
import com.developer.copilot.user.service.UserService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, UserProfileController.class})
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class UserSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void getProfile_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpect(status().isUnauthorized());
        verify(userProfileService, never()).getProfile();
    }

    @Test
    void getResumes_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/resumes"))
                .andExpect(status().isUnauthorized());
        verify(userService, never()).getAllResumes();
    }

    @Test
    void getProfile_garbageBearerToken_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenThrow(new JwtException("bad token"));

        mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        verify(userProfileService, never()).getProfile();
    }

    @Test
    void getProfile_unverifiedEmail_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenReturn(1L);
        User user = new User();
        user.setId(1L);
        user.setEnabled(true);
        user.setEmailVerified(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
        verify(userProfileService, never()).getProfile();
    }

    @Test
    void getProfile_disabledUser_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenReturn(1L);
        User user = new User();
        user.setId(1L);
        user.setEnabled(false);
        user.setEmailVerified(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
        verify(userProfileService, never()).getProfile();
    }

    @Test
    void getResumes_extensionClient_returnsOwnList() throws Exception {
        when(userService.getAllResumes()).thenReturn(List.of(
                ResumeResponse.builder()
                        .id(12L)
                        .originalFilename("Jane-Doe.pdf")
                        .fileSize(120034L)
                        .highPriority(true)
                        .build()));

        mockMvc.perform(get("/api/v1/users/resumes").with(extensionUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(12))
                .andExpect(jsonPath("$.data[0].originalFilename").value("Jane-Doe.pdf"))
                .andExpect(jsonPath("$.data[0].fileSize").value(120034))
                .andExpect(jsonPath("$.data[0].highPriority").value(true));
        verify(userService).getAllResumes();
    }

    @Test
    void getResumes_webClient_returns200() throws Exception {
        when(userService.getAllResumes()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/resumes").with(webUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        verify(userService).getAllResumes();
    }

    @Test
    void getResumes_garbageBearerToken_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenThrow(new JwtException("bad token"));

        mockMvc.perform(get("/api/v1/users/resumes")
                        .header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        verify(userService, never()).getAllResumes();
    }

    @Test
    void resumeRoutesExceptList_extensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users/resumes").with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
        mockMvc.perform(get("/api/v1/users/resumes/12").with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
        mockMvc.perform(delete("/api/v1/users/resumes/12").with(extensionUser()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/resumes/12/high-priority").with(extensionUser()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/profile").with(extensionUser()))
                .andExpect(status().isForbidden());

        verify(userService, never()).uploadResume(any());
        verify(userService, never()).downloadResume(any());
        verify(userService, never()).deleteResume(any());
        verify(userService, never()).setHighPriorityResume(any());
        verify(userProfileService, never()).getProfile();
    }

    @Test
    void getProfile_webClient_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile").with(webUser()))
                .andExpect(status().isOk());
        verify(userProfileService).getProfile();
    }

    @Test
    void clientHeaders_doNotGrantResumeAccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/resumes")
                        .header("X-Client", "browser-extension")
                        .header("Origin", "chrome-extension://lghllhgdcekacmdoolnekkkoffcmcaeo"))
                .andExpect(status().isUnauthorized());
        verify(userService, never()).getAllResumes();
    }

    @Test
    void createProfile_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verify(userProfileService, never()).createProfile(any());
    }

    private static RequestPostProcessor webUser() {
        return user("john@example.com").roles("USER");
    }

    private static RequestPostProcessor extensionUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}
