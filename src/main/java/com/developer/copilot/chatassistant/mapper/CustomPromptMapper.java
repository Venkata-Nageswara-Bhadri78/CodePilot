package com.developer.copilot.chatassistant.mapper;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.chatassistant.dto.request.CustomPromptRequest;
import com.developer.copilot.chatassistant.dto.response.CustomPromptResponse;
import com.developer.copilot.chatassistant.entity.CustomPrompt;

@Component
public class CustomPromptMapper {

    public CustomPrompt toEntity(CustomPromptRequest request, User user) {
        if (request == null) {
            return null;
        }
        return CustomPrompt.builder()
                .user(user)
                .title(trim(request.getTitle()))
                .prompt(trim(request.getPrompt()))
                .build();
    }

    public void updateEntityFromRequest(CustomPrompt entity, CustomPromptRequest request) {
        if (entity == null || request == null) {
            return;
        }
        entity.setTitle(trim(request.getTitle()));
        entity.setPrompt(trim(request.getPrompt()));
    }

    public CustomPromptResponse toResponse(CustomPrompt entity) {
        if (entity == null) {
            return null;
        }
        return CustomPromptResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .prompt(entity.getPrompt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public List<CustomPromptResponse> toResponseList(List<CustomPrompt> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return Collections.emptyList();
        }
        return prompts.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
