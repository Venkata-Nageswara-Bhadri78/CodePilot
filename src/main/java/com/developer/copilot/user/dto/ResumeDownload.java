package com.developer.copilot.user.dto;

import org.springframework.core.io.Resource;

public record ResumeDownload(Resource resource, String filename) {
}
