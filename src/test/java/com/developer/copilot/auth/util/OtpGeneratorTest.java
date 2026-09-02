package com.developer.copilot.auth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpGeneratorTest {

    @Test
    void generateOtp_isSixDigits() {
        for (int i = 0; i < 20; i++) {
            assertTrue(OtpGenerator.generateOtp().matches("\\d{6}"));
        }
    }
}
