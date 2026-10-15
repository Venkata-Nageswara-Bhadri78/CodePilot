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

import com.developer.copilot.auth.dto.AuthResponse;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.auth.security.AuthOnboardingAuthorities;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

    @Test
    void me_withIncompleteWebUser_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").with(incompleteUser()))
                .andExpect(status().isOk());
    }

    @Test
    void google_withoutAuthorization_isPublic() throws Exception {
        when(authService.googleLogin(any())).thenReturn(new AuthResponse("access", "Bearer", "refresh", false));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Complete your username to continue."));
    }

    @Test
    void setupPassword_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/setup-password/request"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/setup-password/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "otp": "123456",
                                  "newPassword": "Secure@123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLink_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLink_withWebUser_isNotForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google/link")
                        .with(user("ada@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Google account linked successfully."));
    }

    @Test
    void googleLink_withExtensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google/link")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    @Test
    void googleLink_withIncompleteWebUser_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google/link")
                        .with(incompleteUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthOnboardingAuthorities.INCOMPLETE_MESSAGE));
    }

    @Test
    void completeUsername_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/complete-username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "newname"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completeUsername_withExtensionClient_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/complete-username")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "newname"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    @Test
    void completeUsername_withIncompleteWebUser_isNotForbidden() throws Exception {
        when(authService.completeUsername(any())).thenReturn(new AuthResponse("access", "Bearer", "refresh", true));

        mockMvc.perform(post("/api/v1/auth/complete-username")
                        .with(incompleteUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "newname"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor incompleteUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthOnboardingAuthorities.INCOMPLETE));
    }

    private static RequestPostProcessor extensionUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}
