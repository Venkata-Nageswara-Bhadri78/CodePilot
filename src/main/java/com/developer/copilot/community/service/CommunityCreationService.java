package com.developer.copilot.community.service;

import com.developer.copilot.community.dto.request.CreateCommunityRequest;
import com.developer.copilot.community.dto.request.ResolveCommunityRequest;
import com.developer.copilot.community.dto.response.CommunityCreateResponse;
import com.developer.copilot.community.dto.response.CommunityDescriptionResponse;
import com.developer.copilot.community.dto.response.CommunityMatchResponse;
import com.developer.copilot.community.dto.response.CommunityPrefillResponse;

public interface CommunityCreationService {

    CommunityPrefillResponse prefill(Long jobId);

    CommunityMatchResponse resolve(ResolveCommunityRequest request);

    CommunityCreateResponse create(CreateCommunityRequest request);

    CommunityDescriptionResponse refine(String description);
}
