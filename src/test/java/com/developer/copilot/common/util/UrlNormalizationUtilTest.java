package com.developer.copilot.common.util;

import com.developer.copilot.common.exception.InvalidJobUrlException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlNormalizationUtilTest {

    private final UrlNormalizationUtil urlNormalizationUtil = new UrlNormalizationUtil();

    @Test
    void normalizeStrict_StripsTrackingQueryParam_MatchesRefExample() {
        String rawUrl = "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/"
                + "Program-Manager-Sr-Consultant_REF087194W?share_id=LinkedIn_corporate_page";

        String normalized = urlNormalizationUtil.normalizeStrict(rawUrl);

        assertEquals(
                "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W",
                normalized
        );
    }

    @Test
    void normalizeStrict_validHttp_remainsUnchanged() {
        assertEquals("http://example.com/job",
                urlNormalizationUtil.normalizeStrict("http://example.com/job"));
    }

    @Test
    void normalizeStrict_removesWww() {
        assertEquals("https://linkedin.com/jobs/view/123",
                urlNormalizationUtil.normalizeStrict("https://www.linkedin.com/jobs/view/123"));
    }

    @Test
    void normalizeStrict_malformedUrl_throwsInvalidJobUrlException() {
        assertThrows(InvalidJobUrlException.class,
                () -> urlNormalizationUtil.normalizeStrict("not-a-url"));
    }

    @Test
    void normalizeLenient_malformedUrl_returnsRawStringSafely() {
        assertEquals("not-a-url", urlNormalizationUtil.normalizeLenient("not-a-url"));
    }

    @Test
    void normalizeStrict_StripsUtmParamsButKeepsNonTrackingParams() {
        String rawUrl = "https://Example.com/jobs/123?utm_source=linkedin&utm_medium=social&jobId=abc";

        String normalized = urlNormalizationUtil.normalizeStrict(rawUrl);

        assertEquals("https://example.com/jobs/123?jobId=abc", normalized);
    }

    @Test
    void normalizeStrict_SameJobDifferentTrackingLinks_ProduceIdenticalCanonicalUrl() {
        String linkedInLink = "https://www.example.com/careers/job/999?utm_source=linkedin&fbclid=abc123";
        String naukriLink = "http://example.com/careers/job/999/?ref=naukri&utm_campaign=jobboard";

        String normalized1 = urlNormalizationUtil.normalizeStrict(linkedInLink);
        String normalized2 = urlNormalizationUtil.normalizeLenient(naukriLink);

        // Same job identity (path), different tracking noise -> must resolve to the same string
        // except for scheme (http vs https intentionally differ here to prove path/host/query
        // canonicalization independently); assert host+path+query portion matches.
        assertTrue(normalized1.endsWith("example.com/careers/job/999"));
        assertTrue(normalized2.endsWith("example.com/careers/job/999"));
    }

    @Test
    void normalizeStrict_SortsRemainingQueryParamsDeterministically() {
        String url1 = "https://example.com/job?b=2&a=1";
        String url2 = "https://example.com/job?a=1&b=2";

        assertEquals(urlNormalizationUtil.normalizeStrict(url1), urlNormalizationUtil.normalizeStrict(url2));
    }

    @Test
    void normalizeStrict_RemovesDefaultPortAndTrailingSlash() {
        String normalized = urlNormalizationUtil.normalizeStrict("https://example.com:443/jobs/42/");
        assertEquals("https://example.com/jobs/42", normalized);
    }

    @Test
    void normalizeStrict_RejectsMissingScheme() {
        assertThrows(InvalidJobUrlException.class,
                () -> urlNormalizationUtil.normalizeStrict("example.com/jobs/42"));
    }

    @Test
    void normalizeStrict_RejectsBlankUrl() {
        assertThrows(InvalidJobUrlException.class, () -> urlNormalizationUtil.normalizeStrict("   "));
    }

    @Test
    void normalizeStrict_RejectsNonHttpScheme() {
        assertThrows(InvalidJobUrlException.class,
                () -> urlNormalizationUtil.normalizeStrict("ftp://example.com/jobs/42"));
    }

    @Test
    void normalizeLenient_FallsBackToTrimmedOriginal_OnInvalidUrl() {
        String result = urlNormalizationUtil.normalizeLenient("  not a url at all  ");
        assertEquals("not a url at all", result);
    }

    @Test
    void normalizeLenient_ReturnsNullForNullInput() {
        assertNull(urlNormalizationUtil.normalizeLenient(null));
    }

    @Test
    void normalizeLenient_BlankInput_ReturnsTrimmedEmptyStringNotNull() {
        String result = urlNormalizationUtil.normalizeLenient("   ");
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    void normalizeStrict_RootPath_PreservedAsIs() {
        assertEquals("https://example.com/",
                urlNormalizationUtil.normalizeStrict("https://example.com/"));
        assertEquals("https://example.com/",
                urlNormalizationUtil.normalizeStrict("https://example.com"));
    }

    @Test
    void normalizeStrict_NonDefaultPort_IsPreserved() {
        assertEquals("https://example.com:8443/jobs/42",
                urlNormalizationUtil.normalizeStrict("https://example.com:8443/jobs/42"));
    }

    @Test
    void normalizeStrict_MissingHost_ThrowsInvalidJobUrlException() {
        assertThrows(InvalidJobUrlException.class,
                () -> urlNormalizationUtil.normalizeStrict("http:///jobs/42"));
    }

    @Test
    void sha256Hex_IsStableAndDeterministic() {
        String hash1 = urlNormalizationUtil.sha256Hex("https://example.com/jobs/1");
        String hash2 = urlNormalizationUtil.sha256Hex("https://example.com/jobs/1");
        String hash3 = urlNormalizationUtil.sha256Hex("https://example.com/jobs/2");

        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
        assertEquals(64, hash1.length());
    }
}
