package com.developer.copilot.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.common.storage.exception.InvalidFileException;
import com.developer.copilot.common.storage.exception.StorageException;
import com.developer.copilot.jobs.exception.JobValidationException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.core.MethodParameter;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logAppender);
    }

    @Test
    void handleValidation_joinsMultipleFieldErrorsWithCommaSeparator() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));
        bindingResult.addError(new FieldError("request", "password", "must be at least 8 characters"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                mockMethodParameter(), bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("email: must not be blank, password: must be at least 8 characters",
                response.getBody().getMessage());
    }

    @Test
    void handleDataIntegrityViolation_returnsGenericMessage_neverLeaksSqlDetails() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Duplicate entry 'user@example.com' for key 'users.email_UNIQUE'");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("The request conflicts with existing data. Please retry.",
                response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("users.email_UNIQUE"));
    }

    @Test
    void handleMessageNotReadable_returnsExpectedMessage() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Request body is missing or malformed JSON.", response.getBody().getMessage());
    }

    @Test
    void handleException_genericCatchAll_returns500AndLogsFullException() {
        RuntimeException ex = new RuntimeException("unexpected null pointer somewhere deep");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Something went wrong.", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());

        boolean logged = logAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains("Unhandled exception occurred"));
        assertTrue(logged, "Expected the generic handler to log the exception at ERROR level");
    }

    @Test
    void handleInvalidFile_returns400_andDoesNotLogAtErrorLevel() {
        InvalidFileException ex = new InvalidFileException("Only PDF uploads are supported.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleInvalidFile(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Only PDF uploads are supported.", response.getBody().getMessage());

        boolean loggedAtError = logAppender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR);
        assertFalse(loggedAtError, "Client-caused file validation failures must not be logged at ERROR");
    }

    @Test
    void handleStorage_hidesTechnicalDetails_andLogsAtError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleStorage(
                new StorageException("Failed against bucket=secret-bucket endpoint=minio:9000"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("A file storage error occurred. Please try again later.", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("secret-bucket"));

        boolean loggedAtError = logAppender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR);
        assertTrue(loggedAtError, "Genuine storage infra failures must be logged at ERROR");
    }

    @Test
    void handleIllegalArgument_mapsTo400_andLogsWarn() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Prior turns cannot exceed 40 entries."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Prior turns cannot exceed 40 entries.", response.getBody().getMessage());

        boolean loggedAtWarn = logAppender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN);
        assertTrue(loggedAtWarn);
    }

    @Test
    void handleJobValidation_mapsTo400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleJobValidation(new JobValidationException("Job title is required."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertNotNull(response.getBody().getTimestamp());
    }

    /**
     * Scans every custom exception class defined under {@code com.developer.copilot} and
     * asserts each concrete, unchecked one has a matching {@code @ExceptionHandler} method in
     * this class, so a future missing handler fails CI instead of silently surfacing as a
     * generic 500 in production.
     */
    @Test
    void everyCustomRuntimeExceptionHasADedicatedHandler() throws ClassNotFoundException {
        Set<Class<?>> handledExceptionTypes = Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(ExceptionHandler.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.toSet());

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(
                java.util.regex.Pattern.compile(".*\\.exception\\..*Exception")));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents("com.developer.copilot");

        List<String> missingHandlers = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            Class<?> exceptionClass = Class.forName(candidate.getBeanClassName());

            if (Modifier.isAbstract(exceptionClass.getModifiers())) {
                continue;
            }
            if (!RuntimeException.class.isAssignableFrom(exceptionClass)) {
                continue;
            }
            // Generic catch-all is intentionally handled by Exception.class itself.
            if (exceptionClass.equals(Exception.class)) {
                continue;
            }
            if (!handledExceptionTypes.contains(exceptionClass)) {
                missingHandlers.add(exceptionClass.getName());
            }
        }

        assertTrue(missingHandlers.isEmpty(),
                "The following custom exceptions have no @ExceptionHandler in GlobalExceptionHandler: "
                        + missingHandlers);
    }

    private MethodParameter mockMethodParameter() {
        try {
            Method dummyMethod = DummyValidatedController.class.getDeclaredMethod("dummy", String.class);
            return new MethodParameter(dummyMethod, 0);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class DummyValidatedController {
        @SuppressWarnings("unused")
        void dummy(@NotBlank String value) {
        }
    }
}
