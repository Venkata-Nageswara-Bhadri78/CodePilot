package com.developer.copilot.user.controller;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.user.service.UserProfileService;
import com.developer.copilot.user.service.UserService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void createProfile_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verify(userProfileService, never()).createProfile(any());
    }
}
