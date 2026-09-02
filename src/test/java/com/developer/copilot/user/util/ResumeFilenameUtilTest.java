package com.developer.copilot.user.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeFilenameUtilTest {

    @Test
    void sanitizeForDownload_keepsAllowlistedName() {
        assertEquals("John_Doe-Resume.pdf", ResumeFilenameUtil.sanitizeForDownload("John_Doe-Resume.pdf"));
    }

    @Test
    void sanitizeForDownload_crlfOrQuotes_fallsBack() {
        assertEquals("resume.pdf", ResumeFilenameUtil.sanitizeForDownload("a\r\nContent-Type: text/html"));
        assertEquals("resume.pdf", ResumeFilenameUtil.sanitizeForDownload("evil\" name.pdf"));
    }

    @Test
    void sanitizeForDownload_pathAndSpaces_fallBack() {
        assertEquals("resume.pdf", ResumeFilenameUtil.sanitizeForDownload("../secret.pdf"));
        assertEquals("resume.pdf", ResumeFilenameUtil.sanitizeForDownload("my resume.pdf"));
    }

    @Test
    void sanitizeForDownload_blank_fallsBack() {
        assertEquals("resume.pdf", ResumeFilenameUtil.sanitizeForDownload(" "));
        assertEquals("resume.pdf", ResumeFilenameUtil.sanitizeForDownload(null));
    }

    @Test
    void isTooLong_detectsOver255() {
        assertFalse(ResumeFilenameUtil.isTooLong("resume.pdf"));
        assertTrue(ResumeFilenameUtil.isTooLong("a".repeat(256)));
    }
}
