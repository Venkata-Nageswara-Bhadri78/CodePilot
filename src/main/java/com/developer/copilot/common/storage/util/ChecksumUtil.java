package com.developer.copilot.common.storage.util;

import java.io.InputStream;
import java.security.MessageDigest;

public class ChecksumUtil {

    private ChecksumUtil() {
    }

    public static String generateSha256(InputStream inputStream) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hash = digest.digest();

            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate checksum.", ex);
        }

    }

}