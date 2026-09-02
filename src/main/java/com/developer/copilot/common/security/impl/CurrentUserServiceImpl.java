package com.developer.copilot.common.security.impl;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.common.security.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserServiceImpl implements CurrentUserService {

    @Override
    public User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null) {
            throw new InvalidCredentialsException("User is not authenticated.");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails userDetails)) {
            throw new InvalidCredentialsException("User is not authenticated.");
        }

        User user = userDetails.getUser();
        if (user == null) {
            throw new InvalidCredentialsException("User is not authenticated.");
        }
        return user;
    }
}
