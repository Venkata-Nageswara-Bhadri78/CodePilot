package com.developer.copilot.user.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public final class PdfValidationUtil {

    private PdfValidationUtil() {
    }

    public static boolean hasPdfMagicBytes(MultipartFile file) {
        try {
            byte[] header = file.getInputStream().readNBytes(5);
            return header.length >= 4
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F';
        } catch (IOException ex) {
            return false;
        }
    }
}
