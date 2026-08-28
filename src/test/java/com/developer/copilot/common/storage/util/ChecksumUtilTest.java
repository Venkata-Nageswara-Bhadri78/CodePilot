package com.developer.copilot.common.storage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ChecksumUtilTest {

    @Test
    void generateSha256_producesDeterministicKnownDigest() {
        String input = "hello world";
        String expectedSha256 =
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";

        String actual = ChecksumUtil.generateSha256(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

        assertEquals(expectedSha256, actual);
    }

    @Test
    void generateSha256_emptyInput_producesWellKnownEmptyHash() {
        String expectedEmptyHash =
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        String actual = ChecksumUtil.generateSha256(new ByteArrayInputStream(new byte[0]));

        assertEquals(expectedEmptyHash, actual);
    }
}
