package com.developer.copilot.community.websocket;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.auth.security.AuthOnboardingAuthorities;
import com.developer.copilot.auth.security.CustomUserDetails;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP CONNECT identity. The token is the Authorization header on the CONNECT frame, not the URL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityStompAuthenticator {

    static final String UNAUTHORIZED = "Unauthorized.";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public Authentication authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException(UNAUTHORIZED);
        }
        String jwt = authorizationHeader.substring(7).trim();
        if (jwt.isEmpty()) {
            throw new BadCredentialsException(UNAUTHORIZED);
        }
        try {
            Long userId = jwtService.extractUserId(jwt);
            User user = userId == null ? null : userRepository.findById(userId).orElse(null);
            if (user == null
                    || !Boolean.TRUE.equals(user.getEnabled())
                    || !Boolean.TRUE.equals(user.getEmailVerified())
                    || !jwtService.isTokenValid(jwt, user)) {
                throw new BadCredentialsException(UNAUTHORIZED);
            }
            if (jwtService.isBrowserExtensionClient(jwt)) {
                throw new AccessDeniedException(AuthClientAuthorities.FORBIDDEN_MESSAGE);
            }
            if (user.getUsername() == null) {
                throw new AccessDeniedException(AuthOnboardingAuthorities.INCOMPLETE_MESSAGE);
            }
            CustomUserDetails details = new CustomUserDetails(user);
            return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        } catch (BadCredentialsException | AccessDeniedException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("STOMP authentication failed");
            throw new BadCredentialsException(UNAUTHORIZED);
        }
    }
}
