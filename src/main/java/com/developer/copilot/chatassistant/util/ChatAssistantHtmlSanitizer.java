package com.developer.copilot.chatassistant.util;

import java.util.regex.Pattern;

/**
 * Strips {@code <script>} blocks from model output before persist. Markdown is otherwise left
 * intact so the SPA can still render it as text/markdown.
 */
public final class ChatAssistantHtmlSanitizer {

    private static final Pattern SCRIPT_BLOCK =
            Pattern.compile("(?is)<script[^>]*>.*?</script>");

    private ChatAssistantHtmlSanitizer() {
    }

    public static String stripScripts(String content) {
        if (content == null) {
            return "";
        }
        return SCRIPT_BLOCK.matcher(content).replaceAll("");
    }
}
