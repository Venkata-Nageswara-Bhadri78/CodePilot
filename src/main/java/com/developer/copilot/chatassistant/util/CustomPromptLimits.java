package com.developer.copilot.chatassistant.util;

public final class CustomPromptLimits {

    public static final int MAX_TITLE_LENGTH = 255;

    /** Matches chat-send prompt max so a saved prompt can be pasted into a job chat as-is. */
    public static final int MAX_PROMPT_LENGTH = 8000;

    public static final String NOT_FOUND = "Custom prompt not found.";

    private CustomPromptLimits() {
    }
}
