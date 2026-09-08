package com.developer.copilot.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Server-issued client/channel markers. The browser-extension restriction is enforced from
 * these authorities (copied from a signed JWT {@code cid} claim), never from request headers
 * or Chrome extension origin.
 */
public final class AuthClientAuthorities {

    /** Spring Security authority granted only when the access JWT {@code cid} claim is trusted. */
    public static final String BROWSER_EXTENSION = "CLIENT_BROWSER_EXTENSION";

    /** JWT claim that identifies the client/channel. Absent claim means the web frontend. */
    public static final String CLAIM = "cid";

    /** Sole supported extension client id. Exactly one Chrome extension product. */
    public static final String CLIENT_ID_BROWSER_EXTENSION = "browser-extension";

    public static final String FORBIDDEN_MESSAGE = "This client is not authorized to access this resource.";

    private AuthClientAuthorities() {
    }

    public static boolean isBrowserExtension(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(BROWSER_EXTENSION::equals);
    }

    public static boolean isBrowserExtensionClientId(String clientId) {
        return CLIENT_ID_BROWSER_EXTENSION.equals(clientId);
    }
}
