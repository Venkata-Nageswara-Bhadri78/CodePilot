package com.developer.copilot.auth.mapper;

import org.springframework.stereotype.Component;

import com.developer.copilot.auth.dto.UserResponse;
import com.developer.copilot.auth.entity.User;

@Component
public class AuthMapper {

    public UserResponse toUserResponse(User user) {
        return toUserResponse(user, false);
    }

    public UserResponse toUserResponse(User user, boolean googleLinked) {

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .passwordConfigured(user.getPassword() != null && !user.getPassword().isBlank())
                .googleLinked(googleLinked)
                .build();

    }

}