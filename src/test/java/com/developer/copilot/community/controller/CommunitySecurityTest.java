package com.developer.copilot.community.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.community.dto.CommunityMatchOutcome;
import com.developer.copilot.community.dto.response.CommunityMatchResponse;
import com.developer.copilot.community.service.CommunityCreationService;
import com.developer.copilot.community.service.CommunityMessages;

@WebMvcTest(controllers = CommunityController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class CommunitySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityCreationService communityCreationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void prefill_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/community/jobs/12/prefill"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized."));
        verify(communityCreationService, never()).prefill(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void create_extensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/community")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
        verify(communityCreationService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolve_webClient_reachesTheService() throws Exception {
        when(communityCreationService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(
                CommunityMatchResponse.builder()
                        .outcome(CommunityMatchOutcome.NO_COMMUNITY)
                        .message(CommunityMessages.NO_MATCH)
                        .build());

        mockMvc.perform(post("/api/v1/community/resolve")
                        .with(user("person@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://jobs.example.com/openings/role\"]}"))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor extensionUser() {
        return user("person@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}
