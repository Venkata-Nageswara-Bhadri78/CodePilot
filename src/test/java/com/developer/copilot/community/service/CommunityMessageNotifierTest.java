package com.developer.copilot.community.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.developer.copilot.community.dto.response.CommunityMessageResponse;

@ExtendWith(MockitoExtension.class)
class CommunityMessageNotifierTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withoutATransaction_publishesImmediately() {
        CommunityMessageNotifier notifier = new CommunityMessageNotifier(eventPublisher);
        CommunityMessageResponse message = CommunityMessageResponse.builder().id(4L).build();

        notifier.afterPersist(message);

        ArgumentCaptor<CommunityMessagePersisted> event = ArgumentCaptor.forClass(CommunityMessagePersisted.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(4L, event.getValue().message().getId());
    }

    @Test
    void insideATransaction_publishesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        CommunityMessageNotifier notifier = new CommunityMessageNotifier(eventPublisher);
        CommunityMessageResponse message = CommunityMessageResponse.builder().id(4L).build();

        notifier.afterPersist(message);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        ArgumentCaptor<CommunityMessagePersisted> event = ArgumentCaptor.forClass(CommunityMessagePersisted.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(4L, event.getValue().message().getId());
    }
}
