package com.developer.copilot.community.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.community.config.CommunityAccountProperties;
import com.developer.copilot.community.dto.CommunityConsentMessages;
import com.developer.copilot.community.dto.CommunityConsentState;
import com.developer.copilot.community.dto.request.AcceptCommunityTermsRequest;
import com.developer.copilot.community.entity.CommunityAccount;
import com.developer.copilot.community.exception.CommunityAccountRequiredException;
import com.developer.copilot.community.exception.CommunityConsentRejectedException;
import com.developer.copilot.community.exception.CommunityTermsVersionException;
import com.developer.copilot.community.exception.TermsAcceptanceRequiredException;
import com.developer.copilot.community.repository.CommunityAccountRepository;
import com.developer.copilot.community.service.CommunityAccountAcceptance;
import com.developer.copilot.community.service.CommunityAccountWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class CommunityAccountServiceImplTest {

    @Mock
    private CommunityAccountRepository communityAccountRepository;

    @Mock
    private CurrentUserService currentUserService;

    private CommunityAccountProperties properties;
    private CommunityAccountServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new CommunityAccountProperties();
        properties.setTermsVersion("2026-10");
        CommunityAccountWriter writer = new CommunityAccountWriter(communityAccountRepository);
        service = new CommunityAccountServiceImpl(
                communityAccountRepository,
                writer,
                currentUserService,
                properties);
        user = new User();
        user.setId(7L);
        user.setEmail("secret@example.com");
        user.setPassword("hashed-password");
        user.setFullName("Private Name");
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void currentConsent_withoutAccount_doesNotCreateOne() {
        when(communityAccountRepository.findByUserId(7L)).thenReturn(Optional.empty());

        var consent = service.currentConsent();

        assertEquals(CommunityConsentState.TERMS_ACCEPTANCE_REQUIRED, consent.getState());
        assertFalse(consent.isAccountPresent());
        assertTrue(consent.isTermsAcceptanceRequired());
        assertEquals("2026-10", consent.getCurrentTermsVersion());
        assertNull(consent.getAccount());
        verify(communityAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void currentConsent_staleVersion_requiresReacceptanceAndKeepsTheOldVersion() {
        when(communityAccountRepository.findByUserId(7L)).thenReturn(Optional.of(account("1", time())));

        var consent = service.currentConsent();

        assertEquals(CommunityConsentState.TERMS_REACCEPTANCE_REQUIRED, consent.getState());
        assertTrue(consent.isAccountPresent());
        assertTrue(consent.isTermsAcceptanceRequired());
        assertEquals("1", consent.getAccount().getAcceptedTermsVersion());
        verify(communityAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingConsent_doesNotCreateAnAccount() {
        assertThrows(CommunityConsentRejectedException.class,
                () -> service.acceptCurrentTerms(AcceptCommunityTermsRequest.builder()
                        .accepted(false)
                        .termsVersion("2026-10")
                        .build()));
        assertThrows(CommunityConsentRejectedException.class, () -> service.acceptCurrentTerms(null));
        verify(communityAccountRepository, never()).saveAndFlush(any());
        verify(communityAccountRepository, never()).findByUserId(any());
    }

    @Test
    void wrongTermsVersion_doesNotCreateOrUpdate() {
        CommunityTermsVersionException ex = assertThrows(CommunityTermsVersionException.class,
                () -> service.acceptCurrentTerms(accept("1")));
        assertEquals(CommunityConsentMessages.VERSION_MISMATCH, ex.getMessage());
        verify(communityAccountRepository, never()).saveAndFlush(any());
        verify(communityAccountRepository, never()).findByUserId(any());
    }

    @Test
    void accept_storesCurrentVersionAndTimestamp() throws Exception {
        when(communityAccountRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(communityAccountRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CommunityAccount account = invocation.getArgument(0);
            account.setId(15L);
            account.setCreatedAt(LocalDateTime.of(2026, 10, 15, 16, 40));
            return account;
        });

        CommunityAccountAcceptance acceptance = service.acceptCurrentTerms(accept("  2026-10  "));

        assertEquals(CommunityAccountAcceptance.Kind.CREATED, acceptance.getKind());
        assertEquals(CommunityConsentState.ACCOUNT_ACTIVE, acceptance.getConsent().getState());
        assertFalse(acceptance.getConsent().isTermsAcceptanceRequired());
        assertEquals("2026-10", acceptance.getConsent().getAccount().getAcceptedTermsVersion());
        assertNotNull(acceptance.getConsent().getAccount().getTermsAcceptedAt());
        assertEquals(15L, acceptance.getConsent().getAccount().getId());

        String json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(acceptance.getConsent());
        assertFalse(json.contains("secret@example.com"));
        assertFalse(json.contains("hashed-password"));
        assertFalse(json.contains("Private Name"));
        assertFalse(json.contains("userId"));
    }

    @Test
    void repeatedAccept_sameVersion_doesNotInsertOrChangeTimestamp() {
        LocalDateTime acceptedAt = time();
        CommunityAccount existing = account("2026-10", acceptedAt);
        when(communityAccountRepository.findByUserId(7L)).thenReturn(Optional.of(existing));

        CommunityAccountAcceptance acceptance = service.acceptCurrentTerms(accept("2026-10"));

        assertEquals(CommunityAccountAcceptance.Kind.UNCHANGED, acceptance.getKind());
        assertEquals(acceptedAt, acceptance.getConsent().getAccount().getTermsAcceptedAt());
        assertEquals(CommunityConsentState.ACCOUNT_ACTIVE, acceptance.getConsent().getState());
        verify(communityAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void newerConfiguredVersion_updatesTheSameRow() {
        LocalDateTime original = time();
        CommunityAccount existing = account("2026-01", original);
        when(communityAccountRepository.findByUserId(7L)).thenReturn(Optional.of(existing));
        when(communityAccountRepository.findById(15L)).thenReturn(Optional.of(existing));
        when(communityAccountRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CommunityAccountAcceptance acceptance = service.acceptCurrentTerms(accept("2026-10"));

        assertEquals(CommunityAccountAcceptance.Kind.REACCEPTED, acceptance.getKind());
        assertEquals("2026-10", existing.getAcceptedTermsVersion());
        assertNotEquals(original, existing.getTermsAcceptedAt());
        assertEquals(CommunityConsentState.ACCOUNT_ACTIVE, acceptance.getConsent().getState());
        assertEquals(15L, acceptance.getConsent().getAccount().getId());
    }

    @Test
    void oldConsent_doesNotSatisfyANewConfiguredVersion() {
        when(communityAccountRepository.findByUserId(7L)).thenReturn(Optional.of(account("2026-10", time())));
        properties.setTermsVersion("2026-11");

        TermsAcceptanceRequiredException ex = assertThrows(
                TermsAcceptanceRequiredException.class,
                () -> service.requireActiveAccount());
        assertEquals(CommunityConsentMessages.TERMS_CHANGED, ex.getMessage());

        var consent = service.currentConsent();
        assertEquals(CommunityConsentState.TERMS_REACCEPTANCE_REQUIRED, consent.getState());
        verify(communityAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void requireActiveAccount_withoutRow_doesNotCreateOne() {
        when(communityAccountRepository.findByUserId(7L)).thenReturn(Optional.empty());

        CommunityAccountRequiredException ex = assertThrows(
                CommunityAccountRequiredException.class,
                () -> service.requireActiveAccount());
        assertEquals(CommunityConsentMessages.ACCOUNT_REQUIRED, ex.getMessage());
        verify(communityAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void requireActiveAccount_whenCurrent_returnsTheRow() {
        CommunityAccount existing = account("2026-10", time());
        when(communityAccountRepository.findByUserId(7L)).thenReturn(Optional.of(existing));

        assertEquals(existing, service.requireActiveAccount());
    }

    @Test
    void unrelatedIntegrityFailure_isNotTreatedAsSuccess() {
        when(communityAccountRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(communityAccountRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("fk_community_accounts_user"));

        assertThrows(DataIntegrityViolationException.class, () -> service.acceptCurrentTerms(accept("2026-10")));
    }

    @Test
    void concurrentAccept_keepsASingleAccount() throws Exception {
        ConcurrentHashMap<Long, CommunityAccount> rows = new ConcurrentHashMap<>();
        AtomicLong ids = new AtomicLong(1);
        when(communityAccountRepository.findByUserId(7L))
                .thenAnswer(invocation -> Optional.ofNullable(rows.get(7L)));
        when(communityAccountRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CommunityAccount account = invocation.getArgument(0);
            if (account.getId() == null) {
                long id = ids.getAndIncrement();
                account.setId(id);
                CommunityAccount raced = rows.putIfAbsent(7L, account);
                if (raced != null) {
                    throw new DataIntegrityViolationException("Duplicate entry for key uk_community_account_user");
                }
                return account;
            }
            rows.put(7L, account);
            return account;
        });

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CommunityAccountAcceptance>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return service.acceptCurrentTerms(accept("2026-10"));
            }));
        }
        start.countDown();

        Set<Long> accountIds = new HashSet<>();
        int created = 0;
        for (Future<CommunityAccountAcceptance> future : futures) {
            CommunityAccountAcceptance acceptance = future.get(10, TimeUnit.SECONDS);
            assertEquals(CommunityConsentState.ACCOUNT_ACTIVE, acceptance.getConsent().getState());
            assertEquals("2026-10", acceptance.getConsent().getAccount().getAcceptedTermsVersion());
            accountIds.add(acceptance.getConsent().getAccount().getId());
            if (acceptance.getKind() == CommunityAccountAcceptance.Kind.CREATED) {
                created++;
            }
        }
        pool.shutdownNow();

        assertEquals(1, rows.size());
        assertEquals(1, accountIds.size());
        assertEquals(1, created);
    }

    private AcceptCommunityTermsRequest accept(String version) {
        return AcceptCommunityTermsRequest.builder()
                .accepted(true)
                .termsVersion(version)
                .build();
    }

    private CommunityAccount account(String version, LocalDateTime acceptedAt) {
        CommunityAccount account = new CommunityAccount();
        account.setId(15L);
        account.setUser(user);
        account.setAcceptedTermsVersion(version);
        account.setTermsAcceptedAt(acceptedAt);
        account.setCreatedAt(acceptedAt);
        return account;
    }

    private static LocalDateTime time() {
        return LocalDateTime.of(2026, 1, 2, 3, 4, 5);
    }
}
