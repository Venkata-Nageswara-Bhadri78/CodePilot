package com.developer.copilot.chatassistant.service;

import java.util.List;

import com.developer.copilot.chatassistant.dto.request.CustomPromptRequest;
import com.developer.copilot.chatassistant.dto.response.CustomPromptResponse;

public interface CustomPromptService {

    CustomPromptResponse create(CustomPromptRequest request);

    List<CustomPromptResponse> listMine();

    CustomPromptResponse getById(Long id);

    CustomPromptResponse update(Long id, CustomPromptRequest request);

    void delete(Long id);
}
