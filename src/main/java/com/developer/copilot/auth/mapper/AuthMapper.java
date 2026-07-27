package com.developer.copilot.auth.mapper;

import org.springframework.stereotype.Component;

import com.developer.copilot.auth.dto.UserResponse;
import com.developer.copilot.auth.entity.User;

@Component
public class AuthMapper {

    public UserResponse toUserResponse(User user) {

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();

    }

}