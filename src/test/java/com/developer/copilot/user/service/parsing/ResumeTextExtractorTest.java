package com.developer.copilot.user.service.parsing;

import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.exception.ResumeParsingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeTextExtractorTest {

    private ResumeProperties resumeProperties;
    private ResumeTextExtractor extractor;

    @BeforeEach
    void setUp() {
        resumeProperties = new ResumeProperties();
        extractor = new ResumeTextExtractor(resumeProperties);
    }

    @Test
    void extract_returnsTextAndPageCount() throws IOException {
        byte[] pdf = pdfWithLines("Jane Doe", "SUMMARY", "Backend engineer.");

        ExtractedResumeText extracted = extractor.extract(pdf);

        assertTrue(extracted.text().contains("Jane Doe"));
        assertTrue(extracted.text().contains("Backend engineer."));
        assertEquals(1, extracted.pageCount());
        assertFalse(extracted.truncated());
    }

    @Test
    void extract_normalizesLineEndingsAndBlankRuns() throws IOException {
        byte[] pdf = pdfWithLines("Jane Doe", "", "", "", "SUMMARY");

        ExtractedResumeText extracted = extractor.extract(pdf);

        assertFalse(extracted.text().contains("\r"));
        assertFalse(extracted.text().contains("\n\n\n"));
    }

    @Test
    void extract_truncatesBeyondConfiguredLimit() throws IOException {
        resumeProperties.getParsing().setMaxTextLength(10);
        byte[] pdf = pdfWithLines("This line is comfortably longer than ten characters");

        ExtractedResumeText extracted = extractor.extract(pdf);

        assertEquals(10, extracted.text().length());
        assertTrue(extracted.truncated());
    }

    @Test
    void extract_imageOnlyPdf_throws() throws IOException {
        byte[] pdf = pdfWithNoText();

        ResumeParsingException ex = assertThrows(ResumeParsingException.class,
                () -> extractor.extract(pdf));

        assertTrue(ex.getMessage().contains("no extractable text"));
    }

    @Test
    void extract_extractionForbiddenPdf_throws() throws IOException {
        byte[] pdf = pdfWithExtractionDisallowed();

        ResumeParsingException ex = assertThrows(ResumeParsingException.class,
                () -> extractor.extract(pdf));

        assertTrue(ex.getMessage().contains("does not allow text extraction"));
    }

    @Test
    void extract_passwordProtectedPdf_throws() throws IOException {
        byte[] pdf = pdfWithUserPassword("secret");

        ResumeParsingException ex = assertThrows(ResumeParsingException.class,
                () -> extractor.extract(pdf));

        assertTrue(ex.getMessage().contains("password protected"));
    }

    @Test
    void extract_nonPdfBytes_throws() {
        assertThrows(ResumeParsingException.class,
                () -> extractor.extract("not a pdf at all".getBytes()));
    }

    @Test
    void extract_emptyBytes_throws() {
        assertThrows(ResumeParsingException.class, () -> extractor.extract(new byte[0]));
    }

    @Test
    void extract_tooManyPages_throws() throws IOException {
        resumeProperties.getParsing().setMaxPages(2);
        byte[] pdf = pdfWithBlankPages(3);

        ResumeParsingException ex = assertThrows(ResumeParsingException.class,
                () -> extractor.extract(pdf));

        assertTrue(ex.getMessage().contains("30") || ex.getMessage().contains("pages"));
        assertTrue(ex.getMessage().contains("2"));
    }

    private byte[] pdfWithLines(String... lines) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            writeLines(document, lines);
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithBlankPages(int pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithNoText() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            document.addPage(new PDPage(PDRectangle.A4));
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithExtractionDisallowed() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            writeLines(document, "Jane Doe");

            AccessPermission permission = new AccessPermission();
            permission.setCanExtractContent(false);

            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-password", "", permission);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);

            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithUserPassword(String userPassword) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            writeLines(document, "Jane Doe");

            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-password", userPassword, new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);

            document.save(out);
            return out.toByteArray();
        }
    }

    private void writeLines(PDDocument document, String... lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.setLeading(16f);
            content.newLineAtOffset(50, 750);
            for (String line : lines) {
                content.showText(line);
                content.newLine();
            }
            content.endText();
        }
    }
}
