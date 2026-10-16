package com.developer.copilot.community.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.community.service.CommunityMessageService;

@WebMvcTest(controllers = CommunityMessageController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class CommunityWebSocketHandshakeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityMessageService communityMessageService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void handshake_withoutAuthorization_isNotRejectedAsUnauthorized() throws Exception {
        int status = mockMvc.perform(get("/ws/community"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertNotEquals(401, status);
    }
}
