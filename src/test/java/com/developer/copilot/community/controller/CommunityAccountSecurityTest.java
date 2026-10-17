package com.developer.copilot.community.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.developer.copilot.auth.security.AuthOnboardingAuthorities;
import com.developer.copilot.community.dto.CommunityConsentState;
import com.developer.copilot.community.dto.response.CommunityConsentResponse;
import com.developer.copilot.community.service.CommunityAccountService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = CommunityAccountController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class CommunityAccountSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityAccountService communityAccountService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void currentConsent_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/community/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized."));
        verify(communityAccountService, never()).currentConsent();
    }

    @Test
    void accept_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/community/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"termsVersion\":\"1\"}"))
                .andExpect(status().isUnauthorized());
        verify(communityAccountService, never()).acceptCurrentTerms(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void accept_extensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/community/account")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"termsVersion\":\"1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
        verify(communityAccountService, never()).acceptCurrentTerms(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void currentConsent_incompleteOnboarding_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/community/account").with(onboardingUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthOnboardingAuthorities.INCOMPLETE_MESSAGE));
        verify(communityAccountService, never()).currentConsent();
    }

    @Test
    void currentConsent_webClient_reachesTheService() throws Exception {
        when(communityAccountService.currentConsent()).thenReturn(CommunityConsentResponse.builder()
                .state(CommunityConsentState.TERMS_ACCEPTANCE_REQUIRED)
                .termsAcceptanceRequired(true)
                .currentTermsVersion("1")
                .build());

        mockMvc.perform(get("/api/v1/community/account").with(user("person@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("TERMS_ACCEPTANCE_REQUIRED"));
    }

    private static RequestPostProcessor extensionUser() {
        return user("person@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }

    private static RequestPostProcessor onboardingUser() {
        return user("person@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthOnboardingAuthorities.INCOMPLETE));
    }
}
