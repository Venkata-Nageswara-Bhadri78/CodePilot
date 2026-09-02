package com.developer.copilot.chatassistant.util;

public final class ChatAssistantLimits {

    public static final int MAX_PAGE_SIZE = 50;

    /** Last N turns sent to the model. Matches AI {@code maxPriorTurnsSent} default and stays under the 40 inbound cap. */
    public static final int MAX_PRIOR_TURNS = 16;

    public static final int MAX_CHAT_TITLE_LENGTH = 255;

    public static final String JOB_NOT_FOUND = "Job not found.";

    private ChatAssistantLimits() {
    }
}
