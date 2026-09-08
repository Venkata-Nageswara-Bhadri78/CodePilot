package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class HtmlNoiseStripperTest {

    @Test
    void strip_removesNavFooterCookiesAndRelatedJobs() {
        String html = """
                <html><body>
                  <nav>Main menu</nav>
                  <div class="cookie">Accept cookies</div>
                  <article>Keep this job body</article>
                  <div class="related-jobs">Similar jobs nearby</div>
                  <footer>Legal</footer>
                </body></html>
                """;
        String cleaned = new HtmlNoiseStripper().strip(Jsoup.parse(html)).text();
        assertTrue(cleaned.contains("Keep this job body"));
        assertFalse(cleaned.contains("Main menu"));
        assertFalse(cleaned.contains("Accept cookies"));
        assertFalse(cleaned.contains("Similar jobs nearby"));
        assertFalse(cleaned.contains("Legal"));
    }
}
