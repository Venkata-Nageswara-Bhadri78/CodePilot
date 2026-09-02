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
import com.developer.copilot.chatassistant.service.ChatAssistantService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatAssistantController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class ChatAssistantSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatAssistantService chatAssistantService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void sendMessage_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getChatHistory_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/chat-assistant/jobs/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listMyChats_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/chat-assistant"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteChat_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/chat-assistant/jobs/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessage_garbageToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isUnauthorized());
        verify(chatAssistantService, never()).sendMessage(any(), any());
    }
}
