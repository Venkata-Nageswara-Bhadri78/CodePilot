package com.developer.copilot.common.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.developer.copilot.auth.exception.ResourceAlreadyExistsException;
import com.developer.copilot.common.dto.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
                .getFieldError()
                .getDefaultMessage();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {

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
}