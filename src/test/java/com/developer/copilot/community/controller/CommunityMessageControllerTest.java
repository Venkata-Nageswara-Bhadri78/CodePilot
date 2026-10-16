package com.developer.copilot.community.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.community.dto.response.CommunityMessagePageResponse;
import com.developer.copilot.community.dto.response.CommunityMessageResponse;
import com.developer.copilot.community.dto.response.CommunityMessageSenderResponse;
import com.developer.copilot.community.exception.CommunityMembershipRequiredException;
import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.service.CommunityMessageService;
import com.developer.copilot.community.service.CommunityMessages;

@ExtendWith(MockitoExtension.class)
class CommunityMessageControllerTest {

    @Mock
    private CommunityMessageService communityMessageService;

    @InjectMocks
    private CommunityMessageController communityMessageController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(communityMessageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void history_returnsOnePageWithoutPrivateProfileFields() throws Exception {
        when(communityMessageService.history(10L, null, null)).thenReturn(CommunityMessagePageResponse.builder()
                .communityId(10L)
                .hasMore(true)
                .nextCursor("v1.cursor")
                .messages(List.of(CommunityMessageResponse.builder()
                        .id(42L)
                        .communityId(10L)
                        .content("Hello")
                        .createdAt(LocalDateTime.of(2026, 10, 16, 13, 0))
                        .sender(CommunityMessageSenderResponse.builder()
                                .userId(7L)
                                .username("ada")
                                .fullName("Ada Lovelace")
                                .build())
                        .build()))
                .build());

        mockMvc.perform(get("/api/v1/community/10/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(CommunityMessages.HISTORY))
                .andExpect(jsonPath("$.data.messages[0].id").value(42))
                .andExpect(jsonPath("$.data.messages[0].sender.username").value("ada"))
                .andExpect(jsonPath("$.data.messages[0].sender.email").doesNotExist())
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value("v1.cursor"));
    }

    @Test
    void history_passesTheCursorAndLimit() throws Exception {
        when(communityMessageService.history(10L, 20, "v1.cursor")).thenReturn(CommunityMessagePageResponse.builder()
                .communityId(10L)
                .messages(List.of())
                .hasMore(false)
                .build());

        mockMvc.perform(get("/api/v1/community/10/messages").param("limit", "20").param("cursor", "v1.cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages").isEmpty())
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void departedMember_cannotReadHistory() throws Exception {
        when(communityMessageService.history(10L, null, null))
                .thenThrow(new CommunityMembershipRequiredException(CommunityMessages.MEMBERSHIP_REQUIRED));

        mockMvc.perform(get("/api/v1/community/10/messages"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(CommunityMessages.MEMBERSHIP_REQUIRED));
    }

    @Test
    void invalidMessageCursor_isRejected() throws Exception {
        when(communityMessageService.history(10L, null, "bad"))
                .thenThrow(new CommunityValidationException(CommunityMessages.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/community/10/messages").param("cursor", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CommunityMessages.INVALID_CURSOR));
    }

    @Test
    void nonNumericCommunityId_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/community/abc/messages"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid community id."));
    }
}
