package com.developer.copilot.jobextraction.automatedjobextraction.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.developer.copilot.jobextraction.automatedjobextraction.exception.InvalidAutomatedJobUrlException;

class SsrfProtectionServiceTest {

    private SsrfProtectionService service;

    @BeforeEach
    void setUp() {
        service = new SsrfProtectionService(host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")});
    }

    @Test
    void publicHttpsHostname_allowed() {
        assertDoesNotThrow(() -> service.validate(URI.create("https://careers.example.com/jobs/1")));
    }

    @Test
    void localhost_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://localhost/jobs/1")));
    }

    @Test
    void loopbackIp_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://127.0.0.1/jobs/1")));
    }

    @Test
    void privateRfc1918_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://192.168.1.10/secret")));
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://10.0.0.5/secret")));
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://172.16.0.5/secret")));
    }

    @Test
    void metadataIp_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://169.254.169.254/latest/meta-data")));
    }

    @Test
    void metadataHost_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://metadata.google.internal/")));
    }

    @Test
    void userInfo_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("https://evil.com@example.com/jobs/1")));
    }

    @Test
    void fileScheme_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("file:///etc/passwd")));
    }

    @Test
    void ftpScheme_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("ftp://example.com/job")));
    }

    @Test
    void ipv6Loopback_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://[::1]/jobs")));
    }

    @Test
    void localSuffix_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://jobs.internal/x")));
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://jobs.local/x")));
    }

    @Test
    void hostnameResolvingToPrivate_blocked() {
        SsrfProtectionService privateDns = new SsrfProtectionService(host -> new InetAddress[] {
                InetAddress.getByName("10.1.1.1")
        });
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> privateDns.validate(URI.create("https://example.com/jobs/1")));
    }

    @Test
    void hostnameResolvingToAnyPrivateAmongMany_blocked() {
        SsrfProtectionService mixed = new SsrfProtectionService(host -> new InetAddress[] {
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("127.0.0.1")
        });
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> mixed.validate(URI.create("https://example.com/jobs/1")));
    }

    @Test
    void unknownHost_blocked() {
        SsrfProtectionService failing = new SsrfProtectionService(host -> {
            throw new UnknownHostException(host);
        });
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> failing.validate(URI.create("https://missing.example/jobs/1")));
    }

    @Test
    void dottedNumericHost_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://127.1/jobs")));
    }

    @Test
    void decimalIp_blocked() {
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> service.validate(URI.create("http://2130706433/jobs")));
    }

    @Test
    void cgnatRange_blockedWhenResolved() {
        SsrfProtectionService cgnat = new SsrfProtectionService(host -> new InetAddress[] {
                InetAddress.getByName("100.64.0.1")
        });
        assertThrows(InvalidAutomatedJobUrlException.class,
                () -> cgnat.validate(URI.create("https://example.com/jobs/1")));
    }
}
