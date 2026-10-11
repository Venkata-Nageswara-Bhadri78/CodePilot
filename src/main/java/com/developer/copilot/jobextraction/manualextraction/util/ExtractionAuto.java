package com.developer.copilot.jobextraction.manualextraction.util;

/**
 * {@code isAuto} defaults to on. Only an explicit {@code false} keeps the preview response.
 */
public final class ExtractionAuto {

    private ExtractionAuto() {
    }

    public static boolean enabled(Boolean isAuto) {
        return !Boolean.FALSE.equals(isAuto);
    }
}
