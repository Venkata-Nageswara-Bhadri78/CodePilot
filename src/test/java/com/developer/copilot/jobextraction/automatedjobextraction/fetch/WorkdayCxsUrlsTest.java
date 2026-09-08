package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

class WorkdayCxsUrlsTest {

    @Test
    void localeSiteJob_mapsToCxs() {
        URI page = URI.create(
                "https://visa.wd5.myworkdayjobs.com/en-US/Visa/job/IN---Bengaluru-India/Engineer_REF1");
        URI cxs = WorkdayCxsUrls.toCxsJobUri(page);
        assertEquals(
                "https://visa.wd5.myworkdayjobs.com/wday/cxs/visa/Visa/job/IN---Bengaluru-India/Engineer_REF1",
                cxs.toString());
    }

    @Test
    void siteJobWithoutLocale_mapsToCxs() {
        URI page = URI.create(
                "https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite/job/Israel-Raanana/DevOps-Engineer_JR1");
        URI cxs = WorkdayCxsUrls.toCxsJobUri(page);
        assertEquals(
                "https://nvidia.wd5.myworkdayjobs.com/wday/cxs/nvidia/NVIDIAExternalCareerSite/job/Israel-Raanana/DevOps-Engineer_JR1",
                cxs.toString());
    }

    @Test
    void detailsPath_mapsToJobCxs() {
        URI page = URI.create("https://acme.wd1.myworkdayjobs.com/en-US/External/details/Title_R9");
        URI cxs = WorkdayCxsUrls.toCxsJobUri(page);
        assertEquals(
                "https://acme.wd1.myworkdayjobs.com/wday/cxs/acme/External/job/Title_R9",
                cxs.toString());
    }

    @Test
    void alreadyCxs_returnsSameUri() {
        URI page = URI.create("https://visa.wd5.myworkdayjobs.com/wday/cxs/visa/Visa/job/x/y");
        assertEquals(page, WorkdayCxsUrls.toCxsJobUri(page));
    }

    @Test
    void listingWithoutJob_returnsNull() {
        assertNull(WorkdayCxsUrls.toCxsJobUri(URI.create("https://visa.wd5.myworkdayjobs.com/en-US/Visa")));
    }

    @Test
    void spaRedirectAndJobJson_detected() {
        assertTrue(WorkdayCxsUrls.looksLikeSpaRedirect(
                "{\"widget\":\"redirect\",\"url\":\"/en-US/Visa/job/x\",\"externalSpa\":true}"));
        assertTrue(WorkdayCxsUrls.looksLikeJobJson("{\"jobPostingInfo\":{\"title\":\"T\"}}"));
        assertTrue(WorkdayCxsUrls.containsJobPostingSignal("{\"@type\":\"JobPosting\",\"title\":\"T\"}"));
    }
}
