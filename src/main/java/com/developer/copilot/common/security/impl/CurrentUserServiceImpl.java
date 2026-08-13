package com.developer.copilot.common.security.impl;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.common.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserServiceImpl implements CurrentUserService {

    @Override
    public User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        CustomUserDetails userDetails =
                (CustomUserDetails) authentication.getPrincipal();

        return userDetails.getUser();

    }

}