package com.developer.copilot.auth.service.impl;

import com.developer.copilot.auth.config.AuthProperties;
import com.developer.copilot.auth.config.EmailProperties;
import com.developer.copilot.auth.util.EmailTemplateService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private MimeMessage mimeMessage;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        EmailProperties emailProperties = new EmailProperties();
        emailProperties.setFrom("noreply@example.com");
        emailProperties.setSenderName("AI Copilot");
        AuthProperties authProperties = new AuthProperties();
        authProperties.setOtpExpiryMinutes(12);
        authProperties.setResetExpiryMinutes(30);
        emailService = new EmailServiceImpl(mailSender, emailProperties, emailTemplateService, authProperties);
    }

    private void stubMail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(emailTemplateService.process(any(), any())).thenReturn("<html></html>");
    }

    @Test
    void sendOtpEmail_usesConfiguredExpiryMinutes() {
        stubMail();
        emailService.sendOtpEmail("john@example.com", "John", "123456");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateService).process(eq("otp-email"), captor.capture());
        assertEquals(12L, captor.getValue().get("expiryMinutes"));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPasswordResetEmail_usesConfiguredExpiryMinutes() {
        stubMail();
        emailService.sendPasswordResetEmail("john@example.com", "John", "reset-token");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateService).process(eq("password-reset"), captor.capture());
        assertEquals(30L, captor.getValue().get("expiryMinutes"));
    }

    @Test
    void validateMailProperties_rejectsBlankFrom() {
        EmailProperties blank = new EmailProperties();
        EmailServiceImpl invalid = new EmailServiceImpl(mailSender, blank, emailTemplateService, new AuthProperties());
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(invalid, "validateMailProperties"));
    }
}
