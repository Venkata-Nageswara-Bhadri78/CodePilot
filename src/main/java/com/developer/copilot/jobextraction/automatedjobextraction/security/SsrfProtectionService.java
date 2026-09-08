package com.developer.copilot.jobextraction.automatedjobextraction.security;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.exception.InvalidAutomatedJobUrlException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SSRF guard for user-supplied job URLs. Only public http(s) hostnames are allowed.
 * Literal IPs, private/loopback/link-local/metadata ranges, credentials in the URL,
 * and unsafe host suffixes are rejected as {@code INVALID JOB URL} so internals are
 * never disclosed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsrfProtectionService {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.|$)){4}");
    private static final Pattern DECIMAL_IPV4 = Pattern.compile("^\\d{8,10}$");
    private static final Pattern NUMERIC_HOST = Pattern.compile("^[0-9.]+$");

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata.google.internal",
            "metadata.goog",
            "metadata.internal",
            "instance-data",
            "kubernetes",
            "ip6-localhost",
            "ip6-loopback"
    );

    private static final Set<String> BLOCKED_SUFFIXES = Set.of(
            ".localhost",
            ".local",
            ".internal",
            ".intranet",
            ".corp",
            ".home",
            ".lan",
            ".private",
            ".onion"
    );

    private final HostnameResolver hostnameResolver;

    public URI validate(URI uri) {
        if (uri == null) {
            throw new InvalidAutomatedJobUrlException();
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidAutomatedJobUrlException();
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new InvalidAutomatedJobUrlException();
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidAutomatedJobUrlException();
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            throw new InvalidAutomatedJobUrlException();
        }
        if (isBlockedHost(normalizedHost) || isLiteralIp(normalizedHost)) {
            throw new InvalidAutomatedJobUrlException();
        }

        String asciiHost;
        try {
            asciiHost = IDN.toASCII(normalizedHost, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new InvalidAutomatedJobUrlException();
        }
        if (asciiHost.isBlank() || isBlockedHost(asciiHost) || isLiteralIp(asciiHost)) {
            throw new InvalidAutomatedJobUrlException();
        }

        InetAddress[] addresses;
        try {
            addresses = hostnameResolver.resolve(asciiHost);
        } catch (UnknownHostException ex) {
            log.debug("SSRF DNS resolution failed for host length {}", asciiHost.length());
            throw new InvalidAutomatedJobUrlException();
        }
        if (addresses == null || addresses.length == 0) {
            throw new InvalidAutomatedJobUrlException();
        }
        for (InetAddress address : addresses) {
            if (address == null || isOffLimits(address)) {
                throw new InvalidAutomatedJobUrlException();
            }
        }
        return uri;
    }

    boolean isBlockedHost(String host) {
        if (BLOCKED_HOSTS.contains(host)) {
            return true;
        }
        for (String suffix : BLOCKED_SUFFIXES) {
            if (host.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    boolean isLiteralIp(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        return IPV4_PATTERN.matcher(host).matches()
                || DECIMAL_IPV4.matcher(host).matches()
                || NUMERIC_HOST.matcher(host).matches();
    }

    boolean isOffLimits(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address ipv4) {
            return isOffLimitsIpv4(ipv4.getAddress());
        }
        if (address instanceof Inet6Address ipv6) {
            return isOffLimitsIpv6(ipv6);
        }
        return true;
    }

    private static boolean isOffLimitsIpv4(byte[] bytes) {
        int a = bytes[0] & 0xFF;
        int b = bytes[1] & 0xFF;
        if (a == 0) {
            return true;
        }
        if (a == 100 && b >= 64 && b <= 127) {
            return true;
        }
        if (a == 192 && b == 0) {
            return true;
        }
        if (a == 198 && (b == 18 || b == 19)) {
            return true;
        }
        if (a == 169 && b == 254) {
            return true;
        }
        return false;
    }

    private static boolean isOffLimitsIpv6(Inet6Address address) {
        byte[] bytes = address.getAddress();
        if (address.isIPv4CompatibleAddress() || isIpv4Mapped(bytes)) {
            byte[] v4 = new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]};
            try {
                InetAddress mapped = InetAddress.getByAddress(v4);
                return mapped.isAnyLocalAddress()
                        || mapped.isLoopbackAddress()
                        || mapped.isLinkLocalAddress()
                        || mapped.isSiteLocalAddress()
                        || mapped.isMulticastAddress()
                        || isOffLimitsIpv4(v4);
            } catch (UnknownHostException ex) {
                return true;
            }
        }
        // Unique local fc00::/7
        if ((bytes[0] & 0xFE) == 0xFC) {
            return true;
        }
        return false;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF;
    }
}
