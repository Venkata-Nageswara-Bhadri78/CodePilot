package com.developer.copilot.common.exception;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.developer.copilot.auth.exception.EmailDeliveryException;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.exception.InvalidOtpException;
import com.developer.copilot.auth.exception.InvalidPasswordResetTokenException;
import com.developer.copilot.auth.exception.InvalidRefreshTokenException;
import com.developer.copilot.auth.exception.OtpExpiredException;
import com.developer.copilot.auth.exception.PasswordResetTokenExpiredException;
import com.developer.copilot.auth.exception.PasswordResetTokenUsedException;
import com.developer.copilot.auth.exception.RefreshTokenExpiredException;
import com.developer.copilot.auth.exception.RefreshTokenRevokedException;
import com.developer.copilot.auth.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.exception.AiUnavailableException;
import com.developer.copilot.auth.exception.ResourceAlreadyExistsException;
import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.common.metrics.CopilotMetrics;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.exception.JobValidationException;
import com.developer.copilot.jobextraction.exception.EmailNotVerifiedException;
import com.developer.copilot.jobextraction.exception.JobExtractionAiUnavailableException;

import com.developer.copilot.common.storage.exception.InvalidFileException;
import com.developer.copilot.common.storage.exception.StorageException;
import com.developer.copilot.common.storage.exception.StorageObjectNotFoundException;
import com.developer.copilot.user.config.ResumeProperties;
import com.developer.copilot.user.exception.AdditionalProfileInformationNotFoundException;
import com.developer.copilot.user.exception.DuplicateResumeException;
import com.developer.copilot.user.exception.DuplicateUserProfileException;
import com.developer.copilot.user.exception.EducationNotFoundException;
import com.developer.copilot.user.exception.InvalidResumeException;
import com.developer.copilot.user.exception.ProfileItemLimitExceededException;
import com.developer.copilot.user.exception.ProfileLinkNotFoundException;
import com.developer.copilot.user.exception.ProjectNotFoundException;
import com.developer.copilot.user.exception.ResumeLimitExceededException;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.exception.WorkExperienceNotFoundException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Intentional client-facing {@link IllegalArgumentException} messages used by other
     * modules. Anything else is treated as a server bug (500) rather than a 400.
     */
    private static final List<String> CLIENT_ILLEGAL_ARGUMENT_PREFIXES = List.of(
            "Prior turns cannot exceed",
            "Each prior turn must include",
            "HMAC value and secret",
            "username: size must be",
            "page must be >= 0"
    );

    private final ResumeProperties resumeProperties;

    public GlobalExceptionHandler() {
        this(null);
    }

    @Autowired(required = false)
    public GlobalExceptionHandler(ResumeProperties resumeProperties) {
        this.resumeProperties = resumeProperties;
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiServiceException(AiServiceException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(response);
    }

    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiUnavailable(AiUnavailableException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    @ExceptionHandler(JobExtractionAiUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleJobExtractionAiUnavailable(
            JobExtractionAiUnavailableException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailNotVerified(EmailNotVerifiedException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(AiResumePendingException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiResumePending(AiResumePendingException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    /**
     * {@code IllegalArgumentException} is used across the codebase for intentional,
     * hardcoded business-validation messages (e.g. "Prior turns cannot exceed 40 entries."),
     * so it is still mapped to 400 here for backward compatibility. However, this exception
     * type is also thrown by the JDK/libraries for reasons unrelated to a bad client request
     * (a null passed where not expected, an invalid enum lookup, etc.), in which case the
     * client would misleadingly see "bad request" for what is actually a server-side bug.
     * The WARN log below at least gives operators a signal to investigate if this handler
     * fires for an exception type/message that was never meant to reach a client.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage();
        boolean clientFacing = message != null && CLIENT_ILLEGAL_ARGUMENT_PREFIXES.stream()
                .anyMatch(message::startsWith);
        if (!clientFacing) {
            log.error("Unhandled IllegalArgumentException occurred: ", ex);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");
        }
        log.warn("IllegalArgumentException mapped to 400: {}", message);
        return failure(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleJobNotFound(JobNotFoundException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(DuplicateJobException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateJob(DuplicateJobException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(JobValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleJobValidation(JobValidationException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(InvalidJobUrlException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJobUrl(InvalidJobUrlException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceAlreadyExists(
            ResourceAlreadyExistsException ex) {

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message("The request conflicts with existing data. Please retry.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message("Request body is missing or malformed JSON.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "jobId".equals(ex.getName()) ? "Invalid job id." : "Invalid request parameter.";
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(com.developer.copilot.chatassistant.exception.ChatConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleChatConflict(
            com.developer.copilot.chatassistant.exception.ChatConflictException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception occurred: ", ex);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message("Something went wrong.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(
        InvalidCredentialsException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }

    @ExceptionHandler(InvalidOtpException.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidOtp(InvalidOtpException ex) {

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleOtpExpired(OtpExpiredException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
   }

   @ExceptionHandler(InvalidPasswordResetTokenException.class)
   public ResponseEntity<ApiResponse<Void>> handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex) {

       ApiResponse<Void> response = ApiResponse.<Void>builder()
               .success(false)
               .message(ex.getMessage())
               .timestamp(LocalDateTime.now())
               .build();

       return ResponseEntity
               .status(HttpStatus.BAD_REQUEST)
               .body(response);
   }

   @ExceptionHandler(PasswordResetTokenExpiredException.class)
   public ResponseEntity<ApiResponse<Void>> handlePasswordResetTokenExpired(PasswordResetTokenExpiredException ex) {

       ApiResponse<Void> response = ApiResponse.<Void>builder()
               .success(false)
               .message(ex.getMessage())
               .timestamp(LocalDateTime.now())
               .build();

       return ResponseEntity
               .status(HttpStatus.BAD_REQUEST)
               .body(response);
   }

   @ExceptionHandler(PasswordResetTokenUsedException.class)
   public ResponseEntity<ApiResponse<Void>> handlePasswordResetTokenUsed(PasswordResetTokenUsedException ex) {

       ApiResponse<Void> response = ApiResponse.<Void>builder()
               .success(false)
               .message(ex.getMessage())
               .timestamp(LocalDateTime.now())
               .build();

       return ResponseEntity
               .status(HttpStatus.BAD_REQUEST)
               .body(response);
   }

   @ExceptionHandler(InvalidRefreshTokenException.class)
   public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {

       ApiResponse<Void> response = ApiResponse.<Void>builder()
               .success(false)
               .message(ex.getMessage())
               .timestamp(LocalDateTime.now())
               .build();

       return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
   }

   @ExceptionHandler(RefreshTokenExpiredException.class)
   public ResponseEntity<ApiResponse<Void>> handleRefreshTokenExpired(RefreshTokenExpiredException ex) {

       ApiResponse<Void> response = ApiResponse.<Void>builder()
               .success(false)
               .message(ex.getMessage())
               .timestamp(LocalDateTime.now())
               .build();

       return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
   }

   @ExceptionHandler(RefreshTokenRevokedException.class)
   public ResponseEntity<ApiResponse<Void>> handleRefreshTokenRevoked(RefreshTokenRevokedException ex) {

       ApiResponse<Void> response = ApiResponse.<Void>builder()
               .success(false)
               .message(ex.getMessage())
               .timestamp(LocalDateTime.now())
               .build();

       return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
   }

   @ExceptionHandler(EmailDeliveryException.class)
   public ResponseEntity<ApiResponse<Void>> handleEmailDelivery(EmailDeliveryException ex) {
       log.error("Email delivery failed");
       ApiResponse<Void> response = ApiResponse.<Void>builder()
               .success(false)
               .message("Unable to send email. Please try again later.")
               .timestamp(LocalDateTime.now())
               .build();
       return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
   }

   @ExceptionHandler(DuplicateResumeException.class)
   public ResponseEntity<ApiResponse<Void>> handleDuplicateResume(
                DuplicateResumeException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(ex.getMessage())
                                .timestamp(LocalDateTime.now())
                                .build()
                );

   }

   @ExceptionHandler(ResumeNotFoundException.class)
   public ResponseEntity<ApiResponse<Void>> handleResumeNotFound(
                ResumeNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(ex.getMessage())
                                .timestamp(LocalDateTime.now())
                                .build()
                );

    }

    @ExceptionHandler(InvalidResumeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidResume(
        InvalidResumeException ex) {

        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(ex.getMessage())
                                .timestamp(LocalDateTime.now())
                                .build()
                );

    }

    @ExceptionHandler(ResumeLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleResumeLimit(
                ResumeLimitExceededException ex) {

        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(ex.getMessage())
                                .timestamp(LocalDateTime.now())
                                .build()
                );

    }

    @ExceptionHandler(ResumeParsingException.class)
    public ResponseEntity<ApiResponse<Void>> handleResumeParsing(
                ResumeParsingException ex) {

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(ex.getMessage())
                                .timestamp(LocalDateTime.now())
                                .build()
                );

    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorage(
                StorageException ex) {

        log.error("Storage operation failed: {}", ex.getMessage(), ex);

        return ResponseEntity.internalServerError()
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message("A file storage error occurred. Please try again later.")
                                .timestamp(LocalDateTime.now())
                                .build()
                );

    }

    /**
     * Client-caused file validation failures (wrong type, empty file, malformed folder/key).
     * Logged at WARN, not ERROR, so expected "user uploaded the wrong thing" events don't
     * pollute the same log signal used to alert on real storage infrastructure failures.
     */
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFile(InvalidFileException ex) {
        log.warn("Rejected file operation: {}", ex.getMessage());
        CopilotMetrics.increment("copilot.storage.failure", "type", "invalid_file");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(ex.getMessage())
                                .timestamp(LocalDateTime.now())
                                .build()
                );
    }

    @ExceptionHandler(UserProfileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserProfileNotFound(UserProfileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @ExceptionHandler(DuplicateUserProfileException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateUserProfile(DuplicateUserProfileException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @ExceptionHandler(WorkExperienceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWorkExperienceNotFound(WorkExperienceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @ExceptionHandler(EducationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEducationNotFound(EducationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectNotFound(ProjectNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @ExceptionHandler(AdditionalProfileInformationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAdditionalInfoNotFound(AdditionalProfileInformationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @ExceptionHandler(ProfileLinkNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProfileLinkNotFound(ProfileLinkNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(
                RateLimitExceededException ex) {

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(response);
    }

    @ExceptionHandler(com.developer.copilot.jobs.ratelimit.exception.RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleJobsRateLimitExceeded(
                com.developer.copilot.jobs.ratelimit.exception.RateLimitExceededException ex) {

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(response);
    }

    @ExceptionHandler(com.developer.copilot.ai.ratelimit.exception.RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiRateLimitExceeded(
                com.developer.copilot.ai.ratelimit.exception.RateLimitExceededException ex) {

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(response);
    }

    @ExceptionHandler(com.developer.copilot.jobextraction.ratelimit.exception.RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleJobExtractionRateLimitExceeded(
                com.developer.copilot.jobextraction.ratelimit.exception.RateLimitExceededException ex) {

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(response);
    }

    @ExceptionHandler(com.developer.copilot.user.ratelimit.exception.RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserRateLimitExceeded(
                com.developer.copilot.user.ratelimit.exception.RateLimitExceededException ex) {

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(response);
    }

    @ExceptionHandler(com.developer.copilot.chatassistant.ratelimit.exception.RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleChatAssistantRateLimitExceeded(
                com.developer.copilot.chatassistant.ratelimit.exception.RateLimitExceededException ex) {

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(response);
    }

    @ExceptionHandler(ProfileItemLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleProfileItemLimit(ProfileItemLimitExceededException ex) {
        return ResponseEntity.badRequest().body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @ExceptionHandler(StorageObjectNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageObjectNotFound(
                StorageObjectNotFoundException ex) {
        return failure(HttpStatus.NOT_FOUND, "File not found.");
    }

    @ExceptionHandler(com.developer.copilot.common.ratelimit.exception.RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleCommonRateLimitExceeded(
                com.developer.copilot.common.ratelimit.exception.RateLimitExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
                ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = "Invalid request parameter.";
        }
        return failure(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
                HttpRequestMethodNotSupportedException ex) {
        return failure(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
                MissingServletRequestParameterException ex) {
        return failure(HttpStatus.BAD_REQUEST, "Required request parameter is missing.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
                HttpMediaTypeNotSupportedException ex) {
        return failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartTooLarge(MaxUploadSizeExceededException ex) {
        int maxMb = resumeProperties != null ? resumeProperties.getMaxFileSizeMb() : 5;
        return failure(HttpStatus.BAD_REQUEST, "Maximum file size is " + maxMb + " MB.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        if (ex instanceof MaxUploadSizeExceededException maxUpload) {
            return handleMultipartTooLarge(maxUpload);
        }
        return failure(HttpStatus.BAD_REQUEST, "Invalid multipart request.");
    }

    private static ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}