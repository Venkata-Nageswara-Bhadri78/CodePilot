package com.developer.copilot.chatassistant.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.developer.copilot.chatassistant.dto.response.ChatSessionListResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionSummaryResponse;
import com.developer.copilot.chatassistant.entity.ChatMessage;
import com.developer.copilot.chatassistant.entity.ChatSession;
import com.developer.copilot.jobs.entity.JobEntity;

class ChatAssistantMapperTest {

    private final ChatAssistantMapper mapper = new ChatAssistantMapper();

    @Test
    void toSessionResponse_nullSession_emptyPage() {
        ChatSessionResponse response = mapper.toSessionResponse(42L, null, Page.empty(PageRequest.of(0, 50)));

        assertNull(response.getChatSessionId());
        assertNull(response.getChatTitle());
        assertEquals(42L, response.getJobId());
        assertTrue(response.getMessages().isEmpty());
        assertEquals(0, response.getPage());
        assertEquals(50, response.getSize());
    }

    @Test
    void toMessageResponseList_nullAndEmpty() {
        assertTrue(mapper.toMessageResponseList(null).isEmpty());
        assertTrue(mapper.toMessageResponseList(List.of()).isEmpty());
    }

    @Test
    void toMessageResponse_copiesCreatedAt() {
        LocalDateTime created = LocalDateTime.of(2026, 8, 24, 15, 30);
        ChatMessage message = ChatMessage.builder()
                .id(9L)
                .turnNumber(1)
                .userPrompt("Q")
                .aiResponse("A")
                .build();
        message.setCreatedAt(created);

        assertEquals(created, mapper.toMessageResponse(message).getCreatedAt());
    }

    @Test
    void toSummaryResponse_nullJob_doesNotNpe() {
        ChatSession session = ChatSession.builder().id(1L).job(null).chatTitle("Solo").build();
        ChatSessionSummaryResponse summary = mapper.toSummaryResponse(session);

        assertEquals(1L, summary.getChatSessionId());
        assertNull(summary.getJobId());
        assertNull(summary.getJobTitle());
        assertNull(summary.getCompany());
        assertEquals("Solo", summary.getChatTitle());
    }

    @Test
    void toSummaryResponseList_nullAndEmpty() {
        assertTrue(mapper.toSummaryResponseList(null).isEmpty());
        assertTrue(mapper.toSummaryResponseList(List.of()).isEmpty());
    }

    @Test
    void toListResponse_mapsPage() {
        JobEntity job = JobEntity.builder().id(7L).title("SDE").company("Amazon").build();
        ChatSession session = ChatSession.builder().id(3L).job(job).chatTitle("Amazon - SDE").build();
        ChatSessionListResponse response = mapper.toListResponse(
                new PageImpl<>(List.of(session), PageRequest.of(0, 50), 1));

        assertEquals(1, response.getChats().size());
        assertEquals(7L, response.getChats().get(0).getJobId());
        assertEquals(1L, response.getTotalElements());
        assertEquals(0, response.getPage());
    }
}
