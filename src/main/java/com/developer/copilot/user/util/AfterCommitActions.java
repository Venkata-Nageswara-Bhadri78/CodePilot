package com.developer.copilot.user.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a side effect only after the surrounding database transaction has committed.
 * If there is no transaction (tests, or a non-transactional caller), the action runs
 * immediately so unit tests still see the effect.
 */
@Slf4j
public final class AfterCommitActions {

    private AfterCommitActions() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    log.error("After-commit action failed: {}", ex.getMessage(), ex);
                }
            }
        });
    }
}
