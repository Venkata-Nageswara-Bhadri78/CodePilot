package com.developer.copilot.common.security.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import com.developer.copilot.common.config.InternalApiProperties;

class InternalApiStartupValidatorTest {

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
    void nonProductionCanDisableInternalApi() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setEnabled(false);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        InternalApiStartupValidator validator = new InternalApiStartupValidator(properties, environment);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }
}
