package com.developer.copilot.chatassistant.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.chatassistant.dto.request.CustomPromptRequest;
import com.developer.copilot.chatassistant.dto.response.CustomPromptResponse;
import com.developer.copilot.chatassistant.entity.CustomPrompt;
import com.developer.copilot.chatassistant.exception.CustomPromptNotFoundException;
import com.developer.copilot.chatassistant.mapper.CustomPromptMapper;
import com.developer.copilot.chatassistant.repository.CustomPromptRepository;
import com.developer.copilot.common.security.CurrentUserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomPromptServiceImplTest {

    @Mock
    private CustomPromptRepository customPromptRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Spy
    private CustomPromptMapper customPromptMapper = new CustomPromptMapper();

    @InjectMocks
    private CustomPromptServiceImpl customPromptService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(11L);
        currentUser.setEmail("owner@example.com");
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void create_savesForCurrentUser() {
        CustomPromptRequest request = CustomPromptRequest.builder()
                .title("Resume rewrite")
                .prompt("Align my resume.")
                .build();
        when(customPromptRepository.save(any(CustomPrompt.class))).thenAnswer(invocation -> {
            CustomPrompt saved = invocation.getArgument(0);
            saved.setId(7L);
            saved.setCreatedAt(LocalDateTime.of(2026, 9, 30, 12, 0));
            saved.setUpdatedAt(LocalDateTime.of(2026, 9, 30, 12, 0));
            return saved;
        });

        CustomPromptResponse response = customPromptService.create(request);

        ArgumentCaptor<CustomPrompt> captor = ArgumentCaptor.forClass(CustomPrompt.class);
        verify(customPromptRepository).save(captor.capture());
        assertEquals(currentUser, captor.getValue().getUser());
        assertEquals("Resume rewrite", captor.getValue().getTitle());
        assertEquals(7L, response.getId());
        assertEquals("Resume rewrite", response.getTitle());
        assertEquals("Align my resume.", response.getPrompt());
    }

    @Test
    void listMine_queriesOnlyTheCallerUserId() {
        when(customPromptRepository.findAllByUserIdOrderByUpdatedAtDesc(11L)).thenReturn(List.of());

        List<CustomPromptResponse> result = customPromptService.listMine();

        assertTrue(result.isEmpty());
        verify(customPromptRepository).findAllByUserIdOrderByUpdatedAtDesc(11L);
        verify(customPromptRepository, never()).findAll();
    }

    @Test
    void getById_ownedRow_returnsResponse() {
        CustomPrompt entity = ownedPrompt(7L, "Resume rewrite", "Align my resume.");
        when(customPromptRepository.findByIdAndUserId(7L, 11L)).thenReturn(Optional.of(entity));

        CustomPromptResponse response = customPromptService.getById(7L);

        assertEquals(7L, response.getId());
        assertEquals("Resume rewrite", response.getTitle());
        verify(customPromptRepository, never()).findById(anyLong());
    }

    @Test
    void getById_foreignOrMissing_throwsNotFound() {
        when(customPromptRepository.findByIdAndUserId(100L, 11L)).thenReturn(Optional.empty());

        assertThrows(CustomPromptNotFoundException.class, () -> customPromptService.getById(100L));
        verify(customPromptRepository, never()).findById(anyLong());
    }

    @Test
    void update_ownedRow_replacesTitleAndPrompt() {
        CustomPrompt entity = ownedPrompt(7L, "old", "old body");
        when(customPromptRepository.findByIdAndUserId(7L, 11L)).thenReturn(Optional.of(entity));
        when(customPromptRepository.save(entity)).thenReturn(entity);

        CustomPromptResponse response = customPromptService.update(7L,
                CustomPromptRequest.builder().title("new").prompt("new body").build());

        assertEquals("new", response.getTitle());
        assertEquals("new body", response.getPrompt());
        verify(customPromptRepository).save(entity);
    }

    @Test
    void update_foreignOrMissing_doesNotSave() {
        when(customPromptRepository.findByIdAndUserId(100L, 11L)).thenReturn(Optional.empty());

        assertThrows(CustomPromptNotFoundException.class, () -> customPromptService.update(
                100L, CustomPromptRequest.builder().title("x").prompt("y").build()));
        verify(customPromptRepository, never()).save(any());
        verify(customPromptRepository, never()).findById(anyLong());
    }

    @Test
    void delete_ownedRow_deletesEntity() {
        CustomPrompt entity = ownedPrompt(7L, "Resume rewrite", "Align my resume.");
        when(customPromptRepository.findByIdAndUserId(7L, 11L)).thenReturn(Optional.of(entity));

        customPromptService.delete(7L);

        verify(customPromptRepository).delete(entity);
    }

    @Test
    void delete_foreignOrMissing_doesNotDelete() {
        when(customPromptRepository.findByIdAndUserId(100L, 11L)).thenReturn(Optional.empty());

        assertThrows(CustomPromptNotFoundException.class, () -> customPromptService.delete(100L));
        verify(customPromptRepository, never()).delete(any());
        verify(customPromptRepository, never()).findById(anyLong());
    }

    private CustomPrompt ownedPrompt(Long id, String title, String prompt) {
        CustomPrompt entity = CustomPrompt.builder()
                .id(id)
                .user(currentUser)
                .title(title)
                .prompt(prompt)
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 9, 30, 12, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 9, 30, 12, 0));
        return entity;
    }
}
