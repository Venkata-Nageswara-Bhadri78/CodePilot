package com.developer.copilot.community.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.enums.Role;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.auth.security.AuthOnboardingAuthorities;
import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.community.service.CommunityMessageService;
import com.developer.copilot.community.websocket.dto.CommunitySendPayload;
import com.developer.copilot.community.websocket.dto.CommunitySocketError;

import io.jsonwebtoken.JwtException;

@ExtendWith(MockitoExtension.class)
class CommunityStompAuthenticatorTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommunityMessageService communityMessageService;
    @Mock
    private CommunityPresenceService communityPresenceService;

    private CommunityStompAuthenticator authenticator;
    private User user;

    @BeforeEach
    void setUp() {
        authenticator = new CommunityStompAuthenticator(jwtService, userRepository);
        user = new User();
        user.setId(7L);
        user.setEmail("ada@example.com");
        user.setUsername("ada");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setRole(Role.USER);
    }

    @Test
    void validWebToken_authenticatesTheUser() {
        when(jwtService.extractUserId("token")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));
        when(jwtService.isTokenValid("token", user)).thenReturn(true);
        when(jwtService.isBrowserExtensionClient("token")).thenReturn(false);

        var authentication = authenticator.authenticate("Bearer token");

        assertEquals(7L, CommunityRealtimeAccess.userId(authentication));
    }

    @Test
    void missingToken_isUnauthorized() {
        assertThrows(BadCredentialsException.class, () -> authenticator.authenticate(null));
    }

    @Test
    void invalidToken_isUnauthorizedWithoutEchoingIt() {
        when(jwtService.extractUserId("secret-token")).thenThrow(new JwtException("bad secret-token"));
        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authenticator.authenticate("Bearer secret-token"));
        assertEquals("Unauthorized.", ex.getMessage());
        assertFalse(ex.getMessage().contains("secret-token"));
    }

    @Test
    void extensionToken_isForbidden() {
        when(jwtService.extractUserId("token")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));
        when(jwtService.isTokenValid("token", user)).thenReturn(true);
        when(jwtService.isBrowserExtensionClient("token")).thenReturn(true);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> authenticator.authenticate("Bearer token"));
        assertEquals(AuthClientAuthorities.FORBIDDEN_MESSAGE, ex.getMessage());
    }

    @Test
    void missingUsername_isRejected() {
        user.setUsername(null);
        when(jwtService.extractUserId("token")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));
        when(jwtService.isTokenValid("token", user)).thenReturn(true);
        when(jwtService.isBrowserExtensionClient("token")).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> authenticator.authenticate("Bearer token"));
        assertEquals(AuthOnboardingAuthorities.INCOMPLETE_MESSAGE, ex.getMessage());
    }

    @Test
    void send_usesTheServiceTextAndNotAClientUserId() {
        CommunityStompController controller = new CommunityStompController(communityMessageService, communityPresenceService);
        CommunitySendPayload payload = new CommunitySendPayload();
        payload.setContent("hello");
        User user = this.user;
        var authentication = new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, null);

        controller.send(10L, payload, authentication);

        verify(communityMessageService).send(10L, "hello");
    }

    @Test
    void payload_rejectsAClientUserId() {
        CommunitySendPayload payload = new CommunitySendPayload();
        var ex = assertThrows(com.developer.copilot.community.exception.CommunityValidationException.class,
                () -> payload.rejectUnknownField("userId", 4));
        assertEquals(CommunitySendPayload.FIELDS, ex.getMessage());
    }

    @Test
    void databaseFailure_isASafeSocketError() {
        CommunitySocketError error = CommunitySocketErrors.from(
                new DataIntegrityViolationException("Duplicate entry 'secret' for key 'community_messages'"));
        assertEquals(CommunitySocketError.SEND_FAILED, error.getCode());
        assertEquals(CommunitySocketErrors.SEND_FAILED, error.getMessage());
        assertFalse(error.getMessage().contains("secret"));
        assertFalse(error.getMessage().contains("community_messages"));
    }
}
