package com.developer.copilot.auth.controller;

import com.developer.copilot.auth.config.JsonAuthenticationEntryPoint;
import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.ratelimit.config.AuthRateLimitConfig;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.developer.copilot.auth.security.AuthClientAuthorities;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class, JsonAuthenticationEntryPoint.class, AuthRateLimitConfig.class})
class AuthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void me_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "john@example.com")
    void me_withAuthorization_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk());
    }

    @Test
    void logoutAll_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "some-refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicAuthRoutes_withoutAuthorization_areNotUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "johndoe",
                                  "fullName": "John Doe",
                                  "email": "john@example.com",
                                  "password": "Secure@123"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "john@example.com"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void extensionToken_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/extension-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "john@example.com")
    void extensionToken_withWebUser_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/extension-token"))
                .andExpect(status().isOk());
    }

    @Test
    void me_withExtensionClient_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(user("john@example.com").authorities(
                                new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    @Test
    void extensionToken_withExtensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/extension-token")
                        .with(user("john@example.com").authorities(
                                new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    @Test
    void logout_withExtensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"550e8400-e29b-41d4-a716-446655440000\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    @Test
    void logoutAll_withExtensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all").with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    @Test
    void logout_withWebUser_isNotForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(user("john@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"550e8400-e29b-41d4-a716-446655440000\"}"))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor extensionUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}
