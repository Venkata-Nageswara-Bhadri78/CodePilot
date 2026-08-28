package com.developer.copilot.common.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.exception.InvalidOtpException;
import com.developer.copilot.auth.exception.InvalidPasswordResetTokenException;
import com.developer.copilot.auth.exception.InvalidRefreshTokenException;
import com.developer.copilot.auth.exception.OtpExpiredException;
import com.developer.copilot.auth.exception.PasswordResetTokenExpiredException;
import com.developer.copilot.auth.exception.PasswordResetTokenUsedException;
import com.developer.copilot.auth.exception.RefreshTokenExpiredException;
import com.developer.copilot.auth.exception.RefreshTokenRevokedException;
import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.auth.exception.ResourceAlreadyExistsException;
import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobs.exception.DuplicateJobException;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.exception.JobValidationException;

import lombok.extern.slf4j.Slf4j;
import com.developer.copilot.common.storage.exception.InvalidFileException;
import com.developer.copilot.common.storage.exception.StorageException;
import com.developer.copilot.user.exception.AdditionalProfileInformationNotFoundException;
import com.developer.copilot.user.exception.DuplicateResumeException;
import com.developer.copilot.user.exception.DuplicateUserProfileException;
import com.developer.copilot.user.exception.EducationNotFoundException;
import com.developer.copilot.user.exception.InvalidResumeException;
import com.developer.copilot.user.exception.ProfileLinkNotFoundException;
import com.developer.copilot.user.exception.ProjectNotFoundException;
import com.developer.copilot.user.exception.ResumeLimitExceededException;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;
import com.developer.copilot.user.exception.WorkExperienceNotFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
        log.warn("IllegalArgumentException mapped to 400: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
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

       return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
   }

   @ExceptionHandler(RefreshTokenExpiredException.class)
   public ResponseEntity<ApiResponse<Void>> handleRefreshTokenExpired(RefreshTokenExpiredException ex) {

       ApiResponse<Void> response = ApiResponse.<Void>builder()
               .success(false)
               .message(ex.getMessage())
               .timestamp(LocalDateTime.now())
               .build();

       return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
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
}