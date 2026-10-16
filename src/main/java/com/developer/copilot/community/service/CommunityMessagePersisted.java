package com.developer.copilot.community.service;

import com.developer.copilot.community.dto.response.CommunityMessageResponse;

/**
 * A message that has already been stored. Task 6 listens with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so a broadcast cannot run
 * for a message that rolled back.
 */
public record CommunityMessagePersisted(CommunityMessageResponse message) {
}
