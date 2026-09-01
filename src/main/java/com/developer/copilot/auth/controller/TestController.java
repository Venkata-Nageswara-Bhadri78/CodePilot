package com.developer.copilot.auth.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;

import java.time.LocalDateTime;

@Profile("dev")
@RestController
public class TestController {

    @GetMapping("/api/v1/test")
    public ApiResponse<String> test() {
        return ApiResponse.<String>builder()
                .success(true)
                .message("JWT Authentication Successful")
                .data("JWT Authentication Successful")
                .timestamp(LocalDateTime.now())
                .build();
    }

}
