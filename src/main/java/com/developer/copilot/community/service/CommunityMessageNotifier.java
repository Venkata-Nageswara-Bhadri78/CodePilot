package com.developer.copilot.community.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.developer.copilot.community.dto.response.CommunityMessageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Publishes a stored message only after the surrounding transaction commits.
 * With no transaction, the row is already flushed, so the event is published immediately.
 */
@Component
@RequiredArgsConstructor
public class CommunityMessageNotifier {

    private final ApplicationEventPublisher eventPublisher;

    public void afterPersist(CommunityMessageResponse message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(new CommunityMessagePersisted(message));
                }
            });
            return;
        }
        eventPublisher.publishEvent(new CommunityMessagePersisted(message));
    }
}
