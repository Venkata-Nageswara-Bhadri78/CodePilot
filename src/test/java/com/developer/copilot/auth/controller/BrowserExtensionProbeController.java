package com.developer.copilot.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only mappings used to exercise the security filter chain without loading
 * production controllers.
 */
@RestController
@RequestMapping("/api/v1")
class BrowserExtensionProbeController {

    @GetMapping(value = "/job-extraction/probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String jobExtraction() {
        return "ok";
    }

    @PostMapping(value = "/job-extraction/probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String jobExtractionPost() {
        return "ok";
    }

    @GetMapping(value = "/automated-job-extraction/probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String automatedJobExtraction() {
        return "ok";
    }

    @GetMapping(value = "/jobs/probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String jobs() {
        return "ok";
    }

    @GetMapping(value = "/ai/probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String ai() {
        return "ok";
    }

    @GetMapping(value = "/chat-assistant/probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String chat() {
        return "ok";
    }

    @GetMapping(value = "/users/probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String users() {
        return "ok";
    }

    @GetMapping(value = "/internal/resumes/probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String internalResumes() {
        return "ok";
    }

    @GetMapping(value = "/auth/me-probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String authMe() {
        return "ok";
    }
}
