package com.developer.copilot.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.community.dto.response.CommunityMessagePageResponse;
import com.developer.copilot.community.service.CommunityMessageService;
import com.developer.copilot.community.service.CommunityMessages;

@WebMvcTest(controllers = CommunityMessageController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class CommunityMessageSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityMessageService communityMessageService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void history_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/community/10/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized."));
        verify(communityMessageService, never()).history(anyLong(), any(), any());
    }

    @Test
    void history_extensionClient_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/community/10/messages").with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
        verify(communityMessageService, never()).history(anyLong(), any(), any());
    }

    @Test
    void history_webClient_reachesTheService() throws Exception {
        when(communityMessageService.history(10L, null, null)).thenReturn(CommunityMessagePageResponse.builder()
                .communityId(10L)
                .messages(List.of())
                .hasMore(false)
                .build());

        mockMvc.perform(get("/api/v1/community/10/messages")
                        .with(user("person@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(CommunityMessages.HISTORY));
    }

    private static RequestPostProcessor extensionUser() {
        return user("person@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}
