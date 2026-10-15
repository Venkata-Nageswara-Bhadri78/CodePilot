package com.developer.copilot.auth.service.oauth;

import com.developer.copilot.auth.config.AuthProperties;
import com.developer.copilot.auth.dto.GoogleAuthRequest;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.ratelimit.service.AuthRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2ServiceTest {

    @Mock
    private GoogleIdTokenService googleIdTokenService;
    @Mock
    private GoogleAccountWriter googleAccountWriter;
    @Mock
    private AuthRateLimitService authRateLimitService;

    private GoogleOAuth2Service service;

    @BeforeEach
    void setUp() {
        service = new GoogleOAuth2Service(
                googleIdTokenService,
                googleAccountWriter,
                authRateLimitService,
                new AuthProperties());
    }

    @Test
    void authenticate_uniqueRace_rereadsIdentity() {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setIdToken("raw-token");
        VerifiedGoogleIdentity identity = new VerifiedGoogleIdentity("sub", "ada@example.com", "Ada");
        User recovered = new User();
        recovered.setId(3L);

        when(googleIdTokenService.verify("raw-token")).thenReturn(identity);
        when(googleAccountWriter.loginOrCreate(identity)).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(googleAccountWriter.recover(identity)).thenReturn(recovered);

        User user = service.authenticate(request);

        assertEquals(3L, user.getId());
        verify(authRateLimitService).consumeOrThrow("google-email", "ada@example.com", 5, 60);
        verify(googleAccountWriter).recover(identity);
    }

    @Test
    void link_uniqueRace_rereadsAndKeepsTheWinner() {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setIdToken("raw-token");
        VerifiedGoogleIdentity identity = new VerifiedGoogleIdentity("sub", "ada@example.com", "Ada");
        when(googleIdTokenService.verify("raw-token")).thenReturn(identity);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(googleAccountWriter).link(4L, identity);

        service.link(request, 4L);

        verify(authRateLimitService).consumeOrThrow("google-link", "4", 5, 60);
        verify(googleAccountWriter).linkAfterConflict(4L, identity);
    }
}
