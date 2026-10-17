package com.developer.copilot.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;

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
import com.developer.copilot.community.dto.CommunityConsentMessages;
import com.developer.copilot.community.dto.CommunityConsentState;
import com.developer.copilot.community.dto.response.CommunityAccountResponse;
import com.developer.copilot.community.dto.response.CommunityConsentResponse;
import com.developer.copilot.community.exception.CommunityAccountExceptionHandler;
import com.developer.copilot.community.exception.CommunityTermsVersionException;
import com.developer.copilot.community.service.CommunityAccountAcceptance;
import com.developer.copilot.community.service.CommunityAccountService;

@ExtendWith(MockitoExtension.class)
class CommunityAccountControllerTest {

    @Mock
    private CommunityAccountService communityAccountService;

    @InjectMocks
    private CommunityAccountController communityAccountController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(communityAccountController)
                .setControllerAdvice(new GlobalExceptionHandler(), new CommunityAccountExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void currentConsent_returnsTermsRequiredWithoutCreatingAnAccount() throws Exception {
        when(communityAccountService.currentConsent()).thenReturn(CommunityConsentResponse.builder()
                .state(CommunityConsentState.TERMS_ACCEPTANCE_REQUIRED)
                .accountPresent(false)
                .termsAcceptanceRequired(true)
                .currentTermsVersion("1")
                .build());

        mockMvc.perform(get("/api/v1/community/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(CommunityConsentMessages.MUST_ACCEPT_FIRST))
                .andExpect(jsonPath("$.data.state").value("TERMS_ACCEPTANCE_REQUIRED"))
                .andExpect(jsonPath("$.data.accountPresent").value(false))
                .andExpect(jsonPath("$.data.termsAcceptanceRequired").value(true))
                .andExpect(jsonPath("$.data.currentTermsVersion").value("1"))
                .andExpect(jsonPath("$.data.account").value(Matchers.nullValue()));
    }

    @Test
    void accept_missingConsent_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/community/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":false,\"termsVersion\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("accepted: " + CommunityConsentMessages.CONSENT_REQUIRED));
        verify(communityAccountService, never()).acceptCurrentTerms(any());
    }

    @Test
    void accept_blankVersion_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/community/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"termsVersion\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("termsVersion: " + CommunityConsentMessages.TERMS_VERSION_REQUIRED));
        verify(communityAccountService, never()).acceptCurrentTerms(any());
    }

    @Test
    void accept_withUserId_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/community/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"termsVersion\":\"1\",\"userId\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CommunityConsentMessages.UNKNOWN_FIELD));
        verify(communityAccountService, never()).acceptCurrentTerms(any());
    }

    @Test
    void accept_wrongVersion_returnsStableMessage() throws Exception {
        when(communityAccountService.acceptCurrentTerms(any()))
                .thenThrow(new CommunityTermsVersionException(CommunityConsentMessages.VERSION_MISMATCH));

        mockMvc.perform(post("/api/v1/community/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"termsVersion\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CommunityConsentMessages.VERSION_MISMATCH));
    }

    @Test
    void accept_createsAccountWithoutPrivateUserFields() throws Exception {
        when(communityAccountService.acceptCurrentTerms(any())).thenReturn(new CommunityAccountAcceptance(
                CommunityConsentResponse.builder()
                        .state(CommunityConsentState.ACCOUNT_ACTIVE)
                        .accountPresent(true)
                        .termsAcceptanceRequired(false)
                        .currentTermsVersion("1")
                        .account(CommunityAccountResponse.builder()
                                .id(10L)
                                .acceptedTermsVersion("1")
                                .build())
                        .build(),
                CommunityAccountAcceptance.Kind.CREATED));

        mockMvc.perform(post("/api/v1/community/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"termsVersion\":\"1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(CommunityConsentMessages.CREATED))
                .andExpect(jsonPath("$.data.state").value("ACCOUNT_ACTIVE"))
                .andExpect(jsonPath("$.data.account.id").value(10))
                .andExpect(jsonPath("$.data.account.acceptedTermsVersion").value("1"))
                .andExpect(jsonPath("$.data.account.email").doesNotExist())
                .andExpect(jsonPath("$.data.account.password").doesNotExist())
                .andExpect(jsonPath("$.data.account.userId").doesNotExist());
    }

    @Test
    void accept_sameVersion_returns200() throws Exception {
        when(communityAccountService.acceptCurrentTerms(any())).thenReturn(new CommunityAccountAcceptance(
                CommunityConsentResponse.builder()
                        .state(CommunityConsentState.ACCOUNT_ACTIVE)
                        .accountPresent(true)
                        .termsAcceptanceRequired(false)
                        .currentTermsVersion("1")
                        .account(CommunityAccountResponse.builder().id(10L).acceptedTermsVersion("1").build())
                        .build(),
                CommunityAccountAcceptance.Kind.UNCHANGED));

        mockMvc.perform(post("/api/v1/community/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"termsVersion\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(CommunityConsentMessages.ACTIVE));
    }
}
