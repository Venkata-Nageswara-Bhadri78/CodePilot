package com.developer.copilot.user.util;

public final class ResumeFilenameUtil {

    public static final int MAX_FILENAME_LENGTH = 255;
    private static final String DEFAULT_FILENAME = "resume.pdf";

    private ResumeFilenameUtil() {
    }

    /**
     * Content-Disposition safe name: letters, digits, dot, underscore, hyphen.
     * Anything else (CR/LF, quotes, path characters, spaces, Unicode) becomes the default.
     */
    public static String sanitizeForDownload(String filename) {
        if (filename == null || filename.isBlank()) {
            return DEFAULT_FILENAME;
        }
        String stripped = filename.replaceAll("[\\r\\n\"]", "").trim();
        if (stripped.isBlank() || stripped.length() > MAX_FILENAME_LENGTH) {
            return DEFAULT_FILENAME;
        }
        if (!stripped.matches("[A-Za-z0-9._-]+")) {
            return DEFAULT_FILENAME;
        }
        return stripped;
    }

    public static boolean isTooLong(String filename) {
        return filename != null && filename.length() > MAX_FILENAME_LENGTH;
    }
}
