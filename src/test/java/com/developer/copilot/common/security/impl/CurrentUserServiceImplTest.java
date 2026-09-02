package com.developer.copilot.common.security.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.security.CustomUserDetails;

class CurrentUserServiceImplTest {

    private final CurrentUserServiceImpl currentUserService = new CurrentUserServiceImpl();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthenticationInContext_throwsInvalidCredentials() {
        SecurityContextHolder.clearContext();

        assertThrows(InvalidCredentialsException.class, currentUserService::getCurrentUser);
    }

    @Test
    void authenticationPresentButNotAuthenticated_throwsInvalidCredentials() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        Authentication authentication = new TestingAuthenticationToken(userDetails, null);
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(InvalidCredentialsException.class, currentUserService::getCurrentUser);
    }

    @Test
    void authenticatedWithNonCustomUserDetailsPrincipal_throwsInvalidCredentials() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("anonymousUser", null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(InvalidCredentialsException.class, currentUserService::getCurrentUser);
    }

    @Test
    void authenticatedWithValidCustomUserDetails_returnsUser() {
        User user = new User();
        user.setId(42L);

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUser()).thenReturn(user);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User result = currentUserService.getCurrentUser();

        assertEquals(42L, result.getId());
    }

    @Test
    void authenticatedWithNullPrincipal_throwsInvalidCredentials() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(InvalidCredentialsException.class, currentUserService::getCurrentUser);
    }

    @Test
    void authenticatedWithNullUserOnDetails_throwsInvalidCredentials() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUser()).thenReturn(null);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(InvalidCredentialsException.class, currentUserService::getCurrentUser);
    }
}
