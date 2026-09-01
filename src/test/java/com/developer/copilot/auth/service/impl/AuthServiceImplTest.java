package com.developer.copilot.auth.service.impl;

import com.developer.copilot.auth.config.AuthProperties;
import com.developer.copilot.auth.dto.AuthResponse;
import com.developer.copilot.auth.dto.ForgotPasswordRequest;
import com.developer.copilot.auth.dto.LoginRequest;
import com.developer.copilot.auth.dto.LogoutRequest;
import com.developer.copilot.auth.dto.RefreshTokenRequest;
import com.developer.copilot.auth.dto.RegisterRequest;
import com.developer.copilot.auth.dto.ResendOtpRequest;
import com.developer.copilot.auth.dto.ResetPasswordRequest;
import com.developer.copilot.auth.dto.VerifyOtpRequest;
import com.developer.copilot.auth.entity.EmailVerification;
import com.developer.copilot.auth.entity.PasswordResetToken;
import com.developer.copilot.auth.entity.RefreshToken;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.enums.Role;
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
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.mapper.AuthMapper;
import com.developer.copilot.auth.repository.EmailVerificationRepository;
import com.developer.copilot.auth.repository.PasswordResetTokenRepository;
import com.developer.copilot.auth.repository.RefreshTokenRepository;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.auth.service.EmailService;
import com.developer.copilot.auth.util.CredentialDigests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationRepository emailVerificationRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("johndoe");
        user.setFullName("John Doe");
        user.setEmail("john@example.com");
        user.setPassword("hashed-password");
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setTokenVersion(0);

        ReflectionTestUtils.setField(authService, "clock", Clock.systemDefaultZone());
        ReflectionTestUtils.setField(authService, "authMapper", new AuthMapper());
        ReflectionTestUtils.setField(authService, "authProperties", new AuthProperties());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_success_savesUserOtpAndSendsEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("johndoe");
        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setPassword("Secure@123");

        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secure@123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        authService.register(request);

        verify(emailVerificationRepository).deleteByUserId(1L);
        verify(emailVerificationRepository).save(any(EmailVerification.class));
        verify(emailService).sendOtpEmail(eq("john@example.com"), eq("John Doe"), anyString());
    }

    @Test
    void register_existingEmail_throwsResourceAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("johndoe");
        request.setEmail("john@example.com");
        request.setPassword("Secure@123");

        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_existingUsername_throwsResourceAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("johndoe");
        request.setEmail("john@example.com");

        when(userRepository.existsByUsername("johndoe")).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyOtp_success_enablesUser() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("john@example.com");
        request.setOtp("123456");

        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setOtp(CredentialDigests.sha256("123456"));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verification.setVerified(false);

        when(emailVerificationRepository.findTopByUserEmailOrderByCreatedAtDesc("john@example.com"))
                .thenReturn(Optional.of(verification));

        authService.verifyOtp(request);

        assertTrue(user.getEnabled());
        assertTrue(user.getEmailVerified());
        verify(emailVerificationRepository).save(verification);
        verify(userRepository).save(user);
    }

    @Test
    void verifyOtp_invalidOtp_throwsInvalidOtpException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("john@example.com");
        request.setOtp("000000");

        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setOtp(CredentialDigests.sha256("123456"));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verification.setVerified(false);

        when(emailVerificationRepository.findTopByUserEmailOrderByCreatedAtDesc("john@example.com"))
                .thenReturn(Optional.of(verification));

        assertThrows(InvalidOtpException.class, () -> authService.verifyOtp(request));
    }

    @Test
    void verifyOtp_expiredOtp_throwsOtpExpiredException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("john@example.com");
        request.setOtp("123456");

        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setOtp(CredentialDigests.sha256("123456"));
        verification.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        verification.setVerified(false);

        when(emailVerificationRepository.findTopByUserEmailOrderByCreatedAtDesc("john@example.com"))
                .thenReturn(Optional.of(verification));

        assertThrows(OtpExpiredException.class, () -> authService.verifyOtp(request));
    }

    @Test
    void verifyOtp_alreadyUsed_throwsInvalidOtpException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("john@example.com");
        request.setOtp("123456");

        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setOtp(CredentialDigests.sha256("123456"));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verification.setVerified(true);

        when(emailVerificationRepository.findTopByUserEmailOrderByCreatedAtDesc("john@example.com"))
                .thenReturn(Optional.of(verification));

        assertThrows(InvalidOtpException.class, () -> authService.verifyOtp(request));
    }

    @Test
    void resendOtp_success_deletesOldOtpAndSendsEmail() {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail("john@example.com");
        user.setEmailVerified(false);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        authService.resendOtp(request);

        verify(emailVerificationRepository).deleteByUserId(1L);
        verify(emailVerificationRepository).save(any(EmailVerification.class));
        verify(emailService).sendOtpEmail(eq("john@example.com"), eq("John Doe"), anyString());
    }

    @Test
    void resendOtp_alreadyVerified_doesNotSendEmail() {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail("john@example.com");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        authService.resendOtp(request);

        verify(emailVerificationRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString());
    }

    @Test
    void login_success_returnsTokens() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("Secure@123");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secure@123", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.login(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getRefreshToken());
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("wrong");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_unverifiedEmail_throwsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("Secure@123");
        user.setEmailVerified(false);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secure@123", "hashed-password")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_disabledAccount_throwsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("Secure@123");
        user.setEnabled(false);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secure@123", "hashed-password")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void forgotPassword_existingUser_savesTokenAndSendsEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("john@example.com");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository).deleteByUserIdAndUsedFalse(1L);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("john@example.com"), eq("John Doe"), anyString());
    }

    @Test
    void resetPassword_success_updatesPasswordAndRevokesTokens() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("reset-token");
        request.setNewPassword("NewSecure@123");

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setUsed(false);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        RefreshToken activeToken = new RefreshToken();
        activeToken.setRevoked(false);

        when(passwordResetTokenRepository.findByToken(CredentialDigests.sha256("reset-token"))).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewSecure@123")).thenReturn("new-hash");
        when(refreshTokenRepository.findAllByUserIdAndRevokedFalse(1L)).thenReturn(List.of(activeToken));

        authService.resetPassword(request);

        assertTrue(resetToken.getUsed());
        assertTrue(activeToken.getRevoked());
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_expiredToken_throwsPasswordResetTokenExpiredException() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("reset-token");
        request.setNewPassword("NewSecure@123");

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setUsed(false);
        resetToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(passwordResetTokenRepository.findByToken(CredentialDigests.sha256("reset-token"))).thenReturn(Optional.of(resetToken));

        assertThrows(PasswordResetTokenExpiredException.class, () -> authService.resetPassword(request));
    }

    @Test
    void refreshToken_success_rotatesToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user);
        storedToken.setRevoked(false);
        storedToken.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenForUpdate(CredentialDigests.sha256("old-refresh"))).thenReturn(Optional.of(storedToken));
        when(jwtService.generateToken(user)).thenReturn("new-access");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.refreshToken(request);

        assertTrue(storedToken.getRevoked());
        assertEquals("new-access", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getRefreshToken());
    }

    @Test
    void refreshToken_revoked_throwsRefreshTokenRevokedException() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user);
        storedToken.setRevoked(true);
        storedToken.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenForUpdate(CredentialDigests.sha256("old-refresh"))).thenReturn(Optional.of(storedToken));

        assertThrows(RefreshTokenRevokedException.class, () -> authService.refreshToken(request));
    }

    @Test
    void refreshToken_expired_throwsRefreshTokenExpiredException() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user);
        storedToken.setRevoked(false);
        storedToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByTokenForUpdate(CredentialDigests.sha256("old-refresh"))).thenReturn(Optional.of(storedToken));

        assertThrows(RefreshTokenExpiredException.class, () -> authService.refreshToken(request));
    }

    @Test
    void logout_success_revokesToken() {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh-token");

        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenAndRevokedFalse(CredentialDigests.sha256("refresh-token")))
                .thenReturn(Optional.of(refreshToken));

        authService.logout(request);

        assertTrue(refreshToken.getRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void logout_invalidToken_throwsInvalidRefreshTokenException() {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("missing");

        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        when(refreshTokenRepository.findByTokenAndRevokedFalse(CredentialDigests.sha256("missing"))).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> authService.logout(request));
    }

    @Test
    void logoutAllDevices_success_revokesAllActiveTokens() {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        RefreshToken token1 = new RefreshToken();
        token1.setRevoked(false);
        RefreshToken token2 = new RefreshToken();
        token2.setRevoked(false);

        when(refreshTokenRepository.findAllByUserIdAndRevokedFalse(1L)).thenReturn(List.of(token1, token2));

        authService.logoutAllDevices();

        assertTrue(token1.getRevoked());
        assertTrue(token2.getRevoked());
        assertEquals(1, user.getTokenVersion());
        verify(userRepository).save(user);
        verify(refreshTokenRepository).saveAll(List.of(token1, token2));
    }

    @Test
    void resetPassword_usedToken_throwsInvalidPasswordResetTokenException() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("reset-token");
        request.setNewPassword("NewSecure@123");

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setUsed(true);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(passwordResetTokenRepository.findByToken(CredentialDigests.sha256("reset-token"))).thenReturn(Optional.of(resetToken));

        assertThrows(PasswordResetTokenUsedException.class, () -> authService.resetPassword(request));
    }

    @Test
    void logout_otherUsersToken_throwsInvalidRefreshTokenException() {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh-token");

        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        User other = new User();
        other.setId(2L);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(other);
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenAndRevokedFalse(CredentialDigests.sha256("refresh-token")))
                .thenReturn(Optional.of(refreshToken));

        assertThrows(InvalidRefreshTokenException.class, () -> authService.logout(request));
    }

    @Test
    void refreshToken_disabledUser_throwsInvalidRefreshTokenException() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh");
        user.setEnabled(false);

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user);
        storedToken.setRevoked(false);
        storedToken.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenForUpdate(CredentialDigests.sha256("old-refresh")))
                .thenReturn(Optional.of(storedToken));

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refreshToken(request));
    }
}
