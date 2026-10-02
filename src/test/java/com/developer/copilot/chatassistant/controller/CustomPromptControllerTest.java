package com.developer.copilot.chatassistant.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.developer.copilot.chatassistant.dto.response.CustomPromptResponse;
import com.developer.copilot.chatassistant.exception.CustomPromptExceptionHandler;
import com.developer.copilot.chatassistant.exception.CustomPromptNotFoundException;
import com.developer.copilot.chatassistant.service.CustomPromptService;
import com.developer.copilot.common.exception.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomPromptControllerTest {

    @Mock
    private CustomPromptService customPromptService;

    @InjectMocks
    private CustomPromptController customPromptController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(customPromptController)
                .setControllerAdvice(new CustomPromptExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        when(customPromptService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/custom-prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Resume rewrite","prompt":"Align my resume."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Custom prompt created successfully."))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.title").value("Resume rewrite"))
                .andExpect(jsonPath("$.data.prompt").value("Align my resume."));
    }

    @Test
    void create_blankTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/custom-prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","prompt":"Align my resume."}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Title cannot be blank.")));
        verify(customPromptService, never()).create(any());
    }

    @Test
    void create_blankPrompt_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/custom-prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Resume rewrite","prompt":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Prompt cannot be blank.")));
        verify(customPromptService, never()).create(any());
    }

    @Test
    void create_promptExceeds8000Chars_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/custom-prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Resume rewrite","prompt":"%s"}
                                """.formatted("a".repeat(8001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verify(customPromptService, never()).create(any());
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/custom-prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed JSON."));
        verify(customPromptService, never()).create(any());
    }

    @Test
    void listMine_empty_returns200WithEmptyArray() throws Exception {
        when(customPromptService.listMine()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/custom-prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Custom prompts retrieved successfully."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listMine_returnsOwnedPrompts() throws Exception {
        when(customPromptService.listMine()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/custom-prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].title").value("Resume rewrite"));
    }

    @Test
    void getById_owned_returns200() throws Exception {
        when(customPromptService.getById(7L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/custom-prompts/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Custom prompt retrieved successfully."))
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(customPromptService.getById(100L)).thenThrow(new CustomPromptNotFoundException());

        mockMvc.perform(get("/api/v1/custom-prompts/100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Custom prompt not found."));
    }

    @Test
    void getById_nonNumeric_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/custom-prompts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid custom prompt id."));
    }

    @Test
    void update_owned_returns200() throws Exception {
        when(customPromptService.update(eq(7L), any())).thenReturn(
                CustomPromptResponse.builder()
                        .id(7L)
                        .title("Updated title")
                        .prompt("Updated body")
                        .createdAt(LocalDateTime.of(2026, 9, 30, 12, 0))
                        .updatedAt(LocalDateTime.of(2026, 9, 30, 13, 0))
                        .build());

        mockMvc.perform(put("/api/v1/custom-prompts/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated title","prompt":"Updated body"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Custom prompt updated successfully."))
                .andExpect(jsonPath("$.data.title").value("Updated title"));
    }

    @Test
    void update_notFound_returns404() throws Exception {
        when(customPromptService.update(eq(100L), any())).thenThrow(new CustomPromptNotFoundException());

        mockMvc.perform(put("/api/v1/custom-prompts/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated title","prompt":"Updated body"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Custom prompt not found."));
    }

    @Test
    void delete_owned_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/custom-prompts/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Custom prompt deleted successfully."));
        verify(customPromptService).delete(7L);
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new CustomPromptNotFoundException()).when(customPromptService).delete(100L);

        mockMvc.perform(delete("/api/v1/custom-prompts/100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Custom prompt not found."));
    }

    private static CustomPromptResponse sampleResponse() {
        return CustomPromptResponse.builder()
                .id(7L)
                .title("Resume rewrite")
                .prompt("Align my resume.")
                .createdAt(LocalDateTime.of(2026, 9, 30, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 9, 30, 12, 0))
                .build();
    }
}
