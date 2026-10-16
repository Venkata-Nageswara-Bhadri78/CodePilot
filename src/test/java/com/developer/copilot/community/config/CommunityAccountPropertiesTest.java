package com.developer.copilot.community.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CommunityAccountPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultVersion_isOne() {
        assertEquals("1", new CommunityAccountProperties().currentTermsVersion());
    }

    @Test
    void blankVersion_isRejected() {
        CommunityAccountProperties properties = new CommunityAccountProperties();
        properties.setTermsVersion("   ");
        assertFalse(validator.validate(properties).isEmpty());
    }

    @Test
    void currentVersion_isTrimmed() {
        CommunityAccountProperties properties = new CommunityAccountProperties();
        properties.setTermsVersion("  2026-10  ");
        assertEquals("2026-10", properties.currentTermsVersion());
    }
}
