package com.developer.copilot.community.service;

import com.developer.copilot.community.dto.request.AcceptCommunityTermsRequest;
import com.developer.copilot.community.dto.response.CommunityConsentResponse;
import com.developer.copilot.community.entity.CommunityAccount;

public interface CommunityAccountService {

    CommunityConsentResponse currentConsent();

    CommunityAccountAcceptance acceptCurrentTerms(AcceptCommunityTermsRequest request);

    /**
     * Gate for later community features. Does not create an account.
     * Throws when the user has no account, or when the stored terms version is not current.
     */
    CommunityAccount requireActiveAccount();
}
