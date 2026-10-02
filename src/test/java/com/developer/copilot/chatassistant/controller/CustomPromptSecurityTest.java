package com.developer.copilot.chatassistant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.chatassistant.service.CustomPromptService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CustomPromptController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class CustomPromptSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomPromptService customPromptService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void create_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/custom-prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Resume rewrite","prompt":"Align my resume."}
                                """))
                .andExpect(status().isUnauthorized());
        verify(customPromptService, never()).create(any());
    }

    @Test
    void listMine_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/custom-prompts"))
                .andExpect(status().isUnauthorized());
        verify(customPromptService, never()).listMine();
    }

    @Test
    void getById_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/custom-prompts/7"))
                .andExpect(status().isUnauthorized());
        verify(customPromptService, never()).getById(any());
    }

    @Test
    void update_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/custom-prompts/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Resume rewrite","prompt":"Align my resume."}
                                """))
                .andExpect(status().isUnauthorized());
        verify(customPromptService, never()).update(any(), any());
    }

    @Test
    void delete_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/custom-prompts/7"))
                .andExpect(status().isUnauthorized());
        verify(customPromptService, never()).delete(any());
    }

    @Test
    void create_garbageToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/custom-prompts")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Resume rewrite","prompt":"Align my resume."}
                                """))
                .andExpect(status().isUnauthorized());
        verify(customPromptService, never()).create(any());
    }
}
