package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

import java.net.URI;
import java.time.Duration;

/**
 * Outbound GET used to retrieve a job page. Isolated so tests never open sockets.
 */
public interface JobPageHttpClient {

    JobPageHttpResponse get(URI uri, Duration requestTimeout, int maxResponseBytes, String userAgent);

    default JobPageHttpResponse get(
            URI uri, Duration requestTimeout, int maxResponseBytes, String userAgent, String accept) {
        return get(uri, requestTimeout, maxResponseBytes, userAgent);
    }
}
