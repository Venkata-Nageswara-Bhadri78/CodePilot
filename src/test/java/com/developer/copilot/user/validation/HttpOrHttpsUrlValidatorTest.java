package com.developer.copilot.user.validation;

import com.developer.copilot.user.dto.profilelink.ProfileLinkRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpOrHttpsUrlValidatorTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void httpsAndHttp_areValid() {
        assertTrue(validate("https://github.com/me").isEmpty());
        assertTrue(validate("http://example.com/x").isEmpty());
    }

    @Test
    void javascriptDataAndFile_areRejected() {
        assertFalse(validate("javascript:alert(1)").isEmpty());
        assertFalse(validate("data:text/html,hi").isEmpty());
        assertFalse(validate("file:///etc/passwd").isEmpty());
    }

    @Test
    void blankOptional_isValidOnValidatorDirectly() {
        HttpOrHttpsUrlValidator direct = new HttpOrHttpsUrlValidator();
        assertTrue(direct.isValid(null, null));
        assertTrue(direct.isValid("  ", null));
    }

    private Set<ConstraintViolation<ProfileLinkRequest>> validate(String url) {
        ProfileLinkRequest request = new ProfileLinkRequest();
        request.setUrl(url);
        return validator.validate(request);
    }
}
