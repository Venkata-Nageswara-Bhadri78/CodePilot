package com.developer.copilot.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;

public class HttpOrHttpsUrlValidator implements ConstraintValidator<HttpOrHttpsUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("file:")) {
            return false;
        }
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
