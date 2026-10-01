package com.developer.copilot.chatassistant.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.chatassistant.dto.request.CustomPromptRequest;
import com.developer.copilot.chatassistant.dto.response.CustomPromptResponse;
import com.developer.copilot.chatassistant.entity.CustomPrompt;

class CustomPromptMapperTest {

    private final CustomPromptMapper mapper = new CustomPromptMapper();

    @Test
    void toEntity_nullRequest_returnsNull() {
        assertNull(mapper.toEntity(null, new User()));
    }

    @Test
    void toEntity_trimsTitleAndPrompt() {
        User user = new User();
        user.setId(3L);
        CustomPrompt entity = mapper.toEntity(
                CustomPromptRequest.builder().title("  Resume rewrite  ").prompt("  Align my resume.  ").build(),
                user);

        assertEquals(user, entity.getUser());
        assertEquals("Resume rewrite", entity.getTitle());
        assertEquals("Align my resume.", entity.getPrompt());
    }

    @Test
    void updateEntityFromRequest_trimsAndReplaces() {
        CustomPrompt entity = CustomPrompt.builder().title("old").prompt("old body").build();
        mapper.updateEntityFromRequest(entity,
                CustomPromptRequest.builder().title("  new title  ").prompt("  new body  ").build());

        assertEquals("new title", entity.getTitle());
        assertEquals("new body", entity.getPrompt());
    }

    @Test
    void toResponse_copiesFieldsIncludingTimestamps() {
        LocalDateTime created = LocalDateTime.of(2026, 9, 30, 12, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 9, 30, 12, 30);
        CustomPrompt entity = CustomPrompt.builder()
                .id(7L)
                .title("Resume rewrite")
                .prompt("Align my resume.")
                .build();
        entity.setCreatedAt(created);
        entity.setUpdatedAt(updated);

        CustomPromptResponse response = mapper.toResponse(entity);

        assertEquals(7L, response.getId());
        assertEquals("Resume rewrite", response.getTitle());
        assertEquals("Align my resume.", response.getPrompt());
        assertEquals(created, response.getCreatedAt());
        assertEquals(updated, response.getUpdatedAt());
    }

    @Test
    void toResponse_null_returnsNull() {
        assertNull(mapper.toResponse(null));
    }

    @Test
    void toResponseList_nullAndEmpty() {
        assertTrue(mapper.toResponseList(null).isEmpty());
        assertTrue(mapper.toResponseList(List.of()).isEmpty());
    }
}
