package com.developer.copilot.user.controller;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.user.dto.ResumeDownload;
import com.developer.copilot.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadResume_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "%PDF-1.4".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/users/resumes").file(file))
                .andExpect(status().isOk());
    }

    @Test
    void downloadResume_usesOriginalFilename() throws Exception {
        when(userService.downloadResume(1L)).thenReturn(
                new ResumeDownload(
                        new ByteArrayResource("%PDF".getBytes()),
                        "John_Doe_Resume.pdf"
                )
        );

        mockMvc.perform(get("/api/v1/users/resumes/1"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"John_Doe_Resume.pdf\""
                ));
    }

    @Test
    void getAllResumes_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/users/resumes"))
                .andExpect(status().isOk());
    }
}
