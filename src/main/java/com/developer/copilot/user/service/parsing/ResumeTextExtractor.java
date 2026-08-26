package com.developer.copilot.user.service.parsing;

import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.exception.ResumeParsingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Extracts plain text from a resume PDF using PDFBox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeTextExtractor {

    private final ResumeProperties resumeProperties;

    public ExtractedResumeText extract(byte[] pdfBytes) {

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ResumeParsingException("Resume file is empty and cannot be parsed.");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw new ResumeParsingException(
                        "Resume is protected and does not allow text extraction.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String normalized = normalize(stripper.getText(document));

            if (normalized.isBlank()) {
                throw new ResumeParsingException(
                        "Resume contains no extractable text. Scanned or image-only PDFs are not supported.");
            }

            int maxLength = resumeProperties.getParsing().getMaxTextLength();
            boolean truncated = normalized.length() > maxLength;
            if (truncated) {
                log.warn("Resume text truncated from {} to {} characters", normalized.length(), maxLength);
                normalized = normalized.substring(0, maxLength);
            }

            return new ExtractedResumeText(normalized, document.getNumberOfPages(), truncated);

        } catch (InvalidPasswordException ex) {
            throw new ResumeParsingException("Resume is password protected and cannot be parsed.", ex);
        } catch (IOException ex) {
            throw new ResumeParsingException("Resume could not be read as a valid PDF document.", ex);
        }
    }

    /**
     * Collapses the layout artefacts PDF extraction produces: mixed line endings,
     * non-breaking spaces, stray control characters and large vertical gaps.
     */
    private String normalize(String text) {

        if (text == null) {
            return "";
        }

        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace('\u2028', '\n')
                .replace('\u2029', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")
                .replaceAll("[ \t]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n");

        return normalized.trim();
    }
}
