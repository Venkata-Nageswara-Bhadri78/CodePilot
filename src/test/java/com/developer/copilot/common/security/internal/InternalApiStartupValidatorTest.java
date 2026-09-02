package com.developer.copilot.common.security.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import com.developer.copilot.common.config.InternalApiProperties;

class InternalApiStartupValidatorTest {

    private static final String STRONG_KEY = "a-genuinely-long-random-service-key-1234567890";

    @Test
    void productionWithDisabledInternalApi_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(false);
        properties.setKey("secret");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionWithBlankKey_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey("  ");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionWithTooShortKey_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey("short-key");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionWithShortPlaceholderKey_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey("changeme");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        // "changeme" is both too short and a placeholder - either failure reason is acceptable here.
        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionWithLongButPlaceholderDerivedKey_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        // Long enough to pass the length check, but still an obvious unchanged placeholder.
        properties.setKey("please-change-me-before-deploying-to-prod");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionWithValidStrongKey_completesWithoutThrowing() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey(STRONG_KEY);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void profileMatchingIsCaseInsensitive() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey(STRONG_KEY);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("PROD");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void laptopCanDisableInternalApi() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(false);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void disabledOutsideLaptop_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(false);

        MockEnvironment environment = new MockEnvironment();

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void nonProductionSkipsWeakKeyCheck() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey("short");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void stagingWithShortKey_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey("short");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void noProfileWithShortKey_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey("short");

        MockEnvironment environment = new MockEnvironment();

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionWithPaddedStrongKey_completesWithoutThrowing() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey("  " + STRONG_KEY + "  ");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void laptopDevCanDisableInternalApi() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(false);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionWithExampleTokenInLongKey_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey("this-is-an-example-key-that-is-long-enough-32");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionWithStrongPreviousKey_completesWithoutThrowing() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey(STRONG_KEY);
        properties.setPreviousKey("another-genuinely-long-random-service-key-99");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionWithShortPreviousKey_failsStartup() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey(STRONG_KEY);
        properties.setPreviousKey("short");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }
}
