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
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.developer.copilot.auth.exception.EmailDeliveryException;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.exception.ResourceAlreadyExistsException;
import com.developer.copilot.auth.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.chatassistant.exception.ChatConflictException;
import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.common.storage.exception.InvalidFileException;
import com.developer.copilot.common.storage.exception.StorageException;
import com.developer.copilot.common.storage.exception.StorageObjectNotFoundException;
import com.developer.copilot.jobextraction.exception.EmailNotVerifiedException;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.user.config.ResumeProperties;

import jakarta.validation.ConstraintViolationException;

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
    void handleIllegalArgument_unknownMessage_mapsTo500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("unexpected null"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Something went wrong.", response.getBody().getMessage());
    }

    @Test
    void handleInvalidJobUrl_mapsTo400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidJobUrl(new InvalidJobUrlException("Job URL must not be empty."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Job URL must not be empty.", response.getBody().getMessage());
    }

    @Test
    void handleInvalidCredentials_mapsTo401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidCredentials(new InvalidCredentialsException("User is not authenticated."));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void handleEmailDelivery_hidesSmtpDetails() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleEmailDelivery(
                new EmailDeliveryException("smtp://secret", new RuntimeException("auth failed")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Unable to send email. Please try again later.", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("smtp"));
    }

    @Test
    void handleAuthRateLimit_includesRetryAfter() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleRateLimitExceeded(
                new RateLimitExceededException(7));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("7", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    void handleModuleRateLimits_includeRetryAfter() {
        assertEquals("3", handler.handleJobsRateLimitExceeded(
                new com.developer.copilot.jobs.ratelimit.exception.RateLimitExceededException(3))
                .getHeaders().getFirst("Retry-After"));
        assertEquals("4", handler.handleUserRateLimitExceeded(
                new com.developer.copilot.user.ratelimit.exception.RateLimitExceededException(4))
                .getHeaders().getFirst("Retry-After"));
        assertEquals("5", handler.handleChatAssistantRateLimitExceeded(
                new com.developer.copilot.chatassistant.ratelimit.exception.RateLimitExceededException(5))
                .getHeaders().getFirst("Retry-After"));
        assertEquals("6", handler.handleJobExtractionRateLimitExceeded(
                new com.developer.copilot.jobextraction.ratelimit.exception.RateLimitExceededException(6))
                .getHeaders().getFirst("Retry-After"));
    }

    @Test
    void handleEmailNotVerified_mapsTo403() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleEmailNotVerified(new EmailNotVerifiedException());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void handleTypeMismatch_jobIdVsOtherParam() throws Exception {
        MethodParameter param = mockMethodParameter();
        ResponseEntity<ApiResponse<Void>> jobId = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "jobId", param, null));
        ResponseEntity<ApiResponse<Void>> resumeId = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "resumeId", param, null));

        assertEquals("Invalid job id.", jobId.getBody().getMessage());
        assertEquals("Invalid request parameter.", resumeId.getBody().getMessage());
    }

    @Test
    void handleMaxUploadSize_usesResumePropertiesWhenPresent() {
        ResumeProperties properties = new ResumeProperties();
        properties.setMaxFileSizeMb(10);
        GlobalExceptionHandler sized = new GlobalExceptionHandler(properties);

        ResponseEntity<ApiResponse<Void>> response =
                sized.handleMultipartTooLarge(new MaxUploadSizeExceededException(10 * 1024 * 1024));

        assertEquals("Maximum file size is 10 MB.", response.getBody().getMessage());
    }

    @Test
    void handleMaxUploadSize_defaultsToFiveMbWithoutProperties() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMultipartTooLarge(new MaxUploadSizeExceededException(1024));

        assertEquals("Maximum file size is 5 MB.", response.getBody().getMessage());
    }

    @Test
    void handleMultipart_malformed_isNotFileSizeMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMultipart(new MultipartException("Unexpected EOF"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid multipart request.", response.getBody().getMessage());
    }

    @Test
    void handleConstraintViolation_emptySet_usesGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(
                new ConstraintViolationException(java.util.Set.of()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request parameter.", response.getBody().getMessage());
    }

    @Test
    void handleMethodNotSupported_mapsTo405() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals("Method not allowed.", response.getBody().getMessage());
    }

    @Test
    void handleMissingParameter_mapsTo400() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParameter(
                new MissingServletRequestParameterException("q", "String"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Required request parameter is missing.", response.getBody().getMessage());
    }

    @Test
    void handleDuplicateAndConflictTypes_mapTo409() {
        assertEquals(HttpStatus.CONFLICT, handler.handleDuplicateJob(
                new DuplicateJobException("dup")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, handler.handleResourceAlreadyExists(
                new ResourceAlreadyExistsException("exists")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, handler.handleChatConflict(
                new ChatConflictException("conflict")).getStatusCode());
    }

    @Test
    void handleStorageObjectNotFound_mapsTo404WithoutBucket() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleStorageObjectNotFound(
                new StorageObjectNotFoundException("missing in bucket=secret"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("File not found.", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("secret"));
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
