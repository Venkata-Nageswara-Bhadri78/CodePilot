package com.developer.copilot.community.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.developer.copilot.community.dto.CommunityMembershipOutcome;
import com.developer.copilot.community.dto.CommunityMembershipView;
import com.developer.copilot.community.dto.response.CommunityListResponse;
import com.developer.copilot.community.dto.response.CommunityMembershipResponse;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.service.CommunityMembershipService;
import com.developer.copilot.community.service.CommunityMessages;

@WebMvcTest(controllers = CommunityMembershipController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class CommunityMembershipSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityMembershipService communityMembershipService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void list_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/community/memberships"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized."));
        verify(communityMembershipService, never()).listMine();
    }

    @Test
    void join_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/community/10/join"))
                .andExpect(status().isUnauthorized());
        verify(communityMembershipService, never()).join(anyLong());
    }

    @Test
    void members_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/community/10/members"))
                .andExpect(status().isUnauthorized());
        verify(communityMembershipService, never()).members(anyLong());
    }

    @Test
    void join_extensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/community/10/join").with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
        verify(communityMembershipService, never()).join(anyLong());
    }

    @Test
    void list_webClient_reachesTheService() throws Exception {
        when(communityMembershipService.listMine()).thenReturn(CommunityListResponse.builder()
                .communities(List.of())
                .build());

        mockMvc.perform(get("/api/v1/community/memberships")
                        .with(user("person@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(CommunityMessages.LISTED));
    }

    @Test
    void leave_webClient_reachesTheService() throws Exception {
        when(communityMembershipService.leave(10L)).thenReturn(CommunityMembershipResponse.builder()
                .outcome(CommunityMembershipOutcome.NOT_A_MEMBER)
                .message(CommunityMessages.NOT_A_MEMBER)
                .communityId(10L)
                .membership(CommunityMembershipView.NOT_A_MEMBER)
                .membershipStatus(null)
                .creator(false)
                .activeMemberCount(1)
                .build());

        mockMvc.perform(post("/api/v1/community/10/leave")
                        .with(user("person@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("NOT_A_MEMBER"));
    }

    private static RequestPostProcessor extensionUser() {
        return user("person@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}
