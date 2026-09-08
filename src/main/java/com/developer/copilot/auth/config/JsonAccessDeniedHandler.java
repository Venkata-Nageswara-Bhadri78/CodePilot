package com.developer.copilot.auth.config;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper provided = objectMapperProvider.getIfAvailable();
        ObjectMapper mapper = provided == null ? new ObjectMapper() : provided.copy();
        this.objectMapper = mapper
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (AuthClientAuthorities.isBrowserExtension(authentication)) {
            log.warn(
                    "Denied browser-extension client {} {}",
                    request.getMethod(),
                    request.getRequestURI());
        } else {
            log.warn("Denied {} {}", request.getMethod(), request.getRequestURI());
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(AuthClientAuthorities.FORBIDDEN_MESSAGE)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
