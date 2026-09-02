package com.developer.copilot.chatassistant.service;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a callback in a short transaction so {@code sendMessage} can call Gemini with no
 * open persistence context.
 */
@Component
public class ChatAssistantTransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public ChatAssistantTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
