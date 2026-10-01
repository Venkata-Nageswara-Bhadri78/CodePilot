package com.developer.copilot.chatassistant.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.chatassistant.dto.request.CustomPromptRequest;
import com.developer.copilot.chatassistant.dto.response.CustomPromptResponse;
import com.developer.copilot.chatassistant.entity.CustomPrompt;
import com.developer.copilot.chatassistant.exception.CustomPromptNotFoundException;
import com.developer.copilot.chatassistant.mapper.CustomPromptMapper;
import com.developer.copilot.chatassistant.repository.CustomPromptRepository;
import com.developer.copilot.chatassistant.service.CustomPromptService;
import com.developer.copilot.common.security.CurrentUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomPromptServiceImpl implements CustomPromptService {

    private final CustomPromptRepository customPromptRepository;
    private final CurrentUserService currentUserService;
    private final CustomPromptMapper customPromptMapper;

    @Override
    @Transactional
    public CustomPromptResponse create(CustomPromptRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        CustomPrompt entity = customPromptMapper.toEntity(request, currentUser);
        CustomPrompt saved = customPromptRepository.save(entity);
        return customPromptMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomPromptResponse> listMine() {
        User currentUser = currentUserService.getCurrentUser();
        return customPromptMapper.toResponseList(
                customPromptRepository.findAllByUserIdOrderByUpdatedAtDesc(currentUser.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomPromptResponse getById(Long id) {
        return customPromptMapper.toResponse(getOwnedPrompt(id));
    }

    @Override
    @Transactional
    public CustomPromptResponse update(Long id, CustomPromptRequest request) {
        CustomPrompt entity = getOwnedPrompt(id);
        customPromptMapper.updateEntityFromRequest(entity, request);
        CustomPrompt saved = customPromptRepository.save(entity);
        return customPromptMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CustomPrompt entity = getOwnedPrompt(id);
        customPromptRepository.delete(entity);
    }

    private CustomPrompt getOwnedPrompt(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        return customPromptRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(CustomPromptNotFoundException::new);
    }
}
