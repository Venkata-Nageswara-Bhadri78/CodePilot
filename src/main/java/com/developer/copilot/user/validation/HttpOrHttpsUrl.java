package com.developer.copilot.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = HttpOrHttpsUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpOrHttpsUrl {

    String message() default "Must be an http or https URL.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
