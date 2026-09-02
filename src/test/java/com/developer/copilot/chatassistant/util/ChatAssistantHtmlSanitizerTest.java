package com.developer.copilot.chatassistant.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatAssistantHtmlSanitizerTest {

    @Test
    void stripScripts_removesScriptBlock() {
        assertEquals("You match this role.",
                ChatAssistantHtmlSanitizer.stripScripts("<script>alert(1)</script>You match this role."));
    }

    @Test
    void stripScripts_nullBecomesEmpty() {
        assertEquals("", ChatAssistantHtmlSanitizer.stripScripts(null));
    }

    @Test
    void stripScripts_leavesMarkdown() {
        assertEquals("**bold**", ChatAssistantHtmlSanitizer.stripScripts("**bold**"));
    }
}
