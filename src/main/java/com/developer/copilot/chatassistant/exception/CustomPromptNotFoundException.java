package com.developer.copilot.chatassistant.exception;

import com.developer.copilot.chatassistant.util.CustomPromptLimits;

public class CustomPromptNotFoundException extends RuntimeException {

    public CustomPromptNotFoundException() {
        super(CustomPromptLimits.NOT_FOUND);
    }
}
