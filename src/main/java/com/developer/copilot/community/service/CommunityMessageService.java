package com.developer.copilot.community.service;

import com.developer.copilot.community.dto.response.CommunityMessagePageResponse;
import com.developer.copilot.community.dto.response.CommunityMessageResponse;

public interface CommunityMessageService {

    /**
     * Stores one text message for the signed-in user.
     * The sender, id, and timestamp come from the server. Task 6 calls this and does not insert a row itself.
     * A persisted-message event is published only after the save succeeds.
     */
    CommunityMessageResponse send(Long communityId, String content);

    /**
     * One page of history for an active member. Omit the cursor for the newest page.
     * Pass the previous {@code nextCursor} to load the next older page.
     */
    CommunityMessagePageResponse history(Long communityId, Integer limit, String cursor);
}
