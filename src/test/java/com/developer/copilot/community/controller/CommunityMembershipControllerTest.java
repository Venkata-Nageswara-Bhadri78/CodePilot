package com.developer.copilot.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.community.dto.CommunityMembershipOutcome;
import com.developer.copilot.community.dto.CommunityMembershipView;
import com.developer.copilot.community.dto.response.CommunityListResponse;
import com.developer.copilot.community.dto.response.CommunityMemberResponse;
import com.developer.copilot.community.dto.response.CommunityMembersResponse;
import com.developer.copilot.community.dto.response.CommunityMembershipResponse;
import com.developer.copilot.community.dto.response.CommunitySummaryResponse;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.exception.CommunityMembershipExceptionHandler;
import com.developer.copilot.community.exception.CommunityMembershipRequiredException;
import com.developer.copilot.community.exception.CommunityUnavailableException;
import com.developer.copilot.community.service.CommunityMembershipService;
import com.developer.copilot.community.service.CommunityMessages;

@ExtendWith(MockitoExtension.class)
class CommunityMembershipControllerTest {

    @Mock
    private CommunityMembershipService communityMembershipService;

    @InjectMocks
    private CommunityMembershipController communityMembershipController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(communityMembershipController)
                .setControllerAdvice(new GlobalExceptionHandler(), new CommunityMembershipExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void list_returnsOnlyTheCallersCommunities() throws Exception {
        when(communityMembershipService.listMine()).thenReturn(CommunityListResponse.builder()
                .communities(List.of(CommunitySummaryResponse.builder()
                        .id(10L)
                        .communityName("Backend Engineer at Northwind")
                        .title("Backend Engineer")
                        .membership(CommunityMembershipView.MEMBER)
                        .activeMemberCount(2)
                        .build()))
                .build());

        mockMvc.perform(get("/api/v1/community/memberships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(CommunityMessages.LISTED))
                .andExpect(jsonPath("$.data.communities[0].id").value(10))
                .andExpect(jsonPath("$.data.communities[0].membership").value("MEMBER"))
                .andExpect(jsonPath("$.data.communities[0].email").doesNotExist());
    }

    @Test
    void join_returns201() throws Exception {
        when(communityMembershipService.join(10L)).thenReturn(joined(CommunityMembershipOutcome.JOINED));

        mockMvc.perform(post("/api/v1/community/10/join"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.outcome").value("JOINED"))
                .andExpect(jsonPath("$.data.membership").value("MEMBER"))
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    void repeatedJoin_returns200() throws Exception {
        when(communityMembershipService.join(10L)).thenReturn(joined(CommunityMembershipOutcome.ALREADY_MEMBER));

        mockMvc.perform(post("/api/v1/community/10/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("ALREADY_MEMBER"));
    }

    @Test
    void join_withUserId_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/community/10/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CommunityMessages.NO_FIELDS));
        verify(communityMembershipService, never()).join(any());
    }

    @Test
    void leave_returns200() throws Exception {
        when(communityMembershipService.leave(10L)).thenReturn(CommunityMembershipResponse.builder()
                .outcome(CommunityMembershipOutcome.LEFT)
                .message(CommunityMessages.LEFT)
                .communityId(10L)
                .membership(CommunityMembershipView.NOT_A_MEMBER)
                .membershipStatus(MembershipStatus.LEFT)
                .creator(true)
                .activeMemberCount(1)
                .build());

        mockMvc.perform(post("/api/v1/community/10/leave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("LEFT"))
                .andExpect(jsonPath("$.data.creator").value(true))
                .andExpect(jsonPath("$.data.membership").value("NOT_A_MEMBER"));
    }

    @Test
    void members_omitPrivateProfileFields() throws Exception {
        when(communityMembershipService.members(10L)).thenReturn(CommunityMembersResponse.builder()
                .communityId(10L)
                .activeMemberCount(1)
                .members(List.of(CommunityMemberResponse.builder()
                        .userId(7L)
                        .username("ada")
                        .fullName("Ada Lovelace")
                        .membershipStatus(MembershipStatus.ACTIVE)
                        .build()))
                .build());

        mockMvc.perform(get("/api/v1/community/10/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].userId").value(7))
                .andExpect(jsonPath("$.data.members[0].username").value("ada"))
                .andExpect(jsonPath("$.data.members[0].fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.data.members[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].password").doesNotExist());
    }

    @Test
    void departedMember_isForbidden() throws Exception {
        when(communityMembershipService.details(10L))
                .thenThrow(new CommunityMembershipRequiredException(CommunityMessages.MEMBERSHIP_REQUIRED));

        mockMvc.perform(get("/api/v1/community/10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(CommunityMessages.MEMBERSHIP_REQUIRED))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void missingCommunity_isUnavailable() throws Exception {
        when(communityMembershipService.status(44L))
                .thenThrow(new CommunityUnavailableException(CommunityMessages.COMMUNITY_UNAVAILABLE));

        mockMvc.perform(get("/api/v1/community/44/membership"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(CommunityMessages.COMMUNITY_UNAVAILABLE));
    }

    @Test
    void nonNumericCommunityId_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/community/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid community id."));
        verify(communityMembershipService, never()).details(any());
    }

    private static CommunityMembershipResponse joined(CommunityMembershipOutcome outcome) {
        return CommunityMembershipResponse.builder()
                .outcome(outcome)
                .message(outcome.message())
                .communityId(10L)
                .membership(CommunityMembershipView.MEMBER)
                .membershipStatus(MembershipStatus.ACTIVE)
                .creator(false)
                .activeMemberCount(2)
                .build();
    }
}
