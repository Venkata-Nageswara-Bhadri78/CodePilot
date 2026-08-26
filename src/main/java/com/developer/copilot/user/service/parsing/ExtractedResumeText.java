package com.developer.copilot.user.service.parsing;

/**
 * Raw outcome of PDF text extraction, before any section detection.
 *
 * @param text      normalized full text of the document
 * @param pageCount number of pages in the source PDF
 * @param truncated whether the text was cut short by the configured length cap
 */
public record ExtractedResumeText(String text, int pageCount, boolean truncated) {
}
