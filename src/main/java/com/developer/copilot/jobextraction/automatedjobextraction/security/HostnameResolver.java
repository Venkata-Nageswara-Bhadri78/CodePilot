package com.developer.copilot.jobextraction.automatedjobextraction.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves a hostname to addresses. Isolated so SSRF tests never perform real DNS.
 */
@FunctionalInterface
public interface HostnameResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
