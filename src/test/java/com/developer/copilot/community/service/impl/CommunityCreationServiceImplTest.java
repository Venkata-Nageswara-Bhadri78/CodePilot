package com.developer.copilot.community.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.common.util.UrlNormalizationUtil;
import com.developer.copilot.community.config.CommunityAccountProperties;
import com.developer.copilot.community.dto.CommunityMatchOutcome;
import com.developer.copilot.community.dto.CommunityMembershipView;
import com.developer.copilot.community.dto.request.CreateCommunityRequest;
import com.developer.copilot.community.dto.response.CommunityCreateResponse;
import com.developer.copilot.community.dto.response.CommunityPrefillResponse;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityAccount;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.CommunityUrlAlias;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.exception.CommunityAliasConflictException;
import com.developer.copilot.community.exception.CommunityMatchUncertainException;
import com.developer.copilot.community.repository.CommunityMembershipRepository;
import com.developer.copilot.community.repository.CommunityRepository;
import com.developer.copilot.community.repository.CommunityUrlAliasRepository;
import com.developer.copilot.community.service.CommunityAccountService;
import com.developer.copilot.community.service.CommunityCreationWriter;
import com.developer.copilot.community.service.CommunityDescriptionRefiner;
import com.developer.copilot.community.service.CommunitySummaryMapper;
import com.developer.copilot.community.service.matching.CommunityNameGenerator;
import com.developer.copilot.community.service.matching.CommunityUrlMatcher;
import com.developer.copilot.jobs.entity.JobEntity;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.jobs.repository.JobRepository;

@ExtendWith(MockitoExtension.class)
class CommunityCreationServiceImplTest {

    private static final String POSTING = "https://jobs.example.com/openings/backend-engineer";

    @Mock
    private CommunityAccountService communityAccountService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityUrlAliasRepository communityUrlAliasRepository;
    @Mock
    private CommunityMembershipRepository communityMembershipRepository;
    @Mock
    private AiService aiService;

    private CommunityCreationServiceImpl service;
    private User user;
    private JobEntity job;

    @BeforeEach
    void setUp() {
        CommunityAccountProperties properties = new CommunityAccountProperties();
        CommunityUrlMatcher matcher = new CommunityUrlMatcher(new UrlNormalizationUtil(), communityUrlAliasRepository);
        CommunityNameGenerator names = new CommunityNameGenerator(communityRepository);
        CommunityCreationWriter writer = new CommunityCreationWriter(
                communityRepository, communityUrlAliasRepository, communityMembershipRepository);
        service = new CommunityCreationServiceImpl(
                communityAccountService,
                currentUserService,
                jobRepository,
                communityRepository,
                communityUrlAliasRepository,
                matcher,
                names,
                writer,
                new CommunityDescriptionRefiner(aiService),
                properties,
                new CommunitySummaryMapper(communityMembershipRepository));
        user = new User();
        user.setId(7L);
        user.setEmail("secret@example.com");
        user.setPassword("hashed");
        user.setFullName("Private Name");
        job = JobEntity.builder()
                .id(12L)
                .user(user)
                .sourceUrl(POSTING)
                .sourceUrlHash("hash")
                .originalDescription("raw pasted text")
                .description("Build APIs")
                .title("Backend Engineer")
                .company("Northwind")
                .location("Bengaluru")
                .salary("999999")
                .notes("call the recruiter")
                .skills("Java")
                .build();
        lenient().when(communityAccountService.requireActiveAccount()).thenReturn(new CommunityAccount());
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(jobRepository.findByIdAndUserId(12L, 7L)).thenReturn(Optional.of(job));
        lenient().when(communityUrlAliasRepository.findByNormalizedUrlHash(any())).thenReturn(Optional.empty());
        lenient().when(communityRepository.existsByCommunityName(any())).thenReturn(false);
        lenient().when(communityRepository.findByCanonicalPostingKey(any())).thenReturn(Optional.empty());
    }

    @Test
    void prefill_returnsPermittedFieldsAndDoesNotCreate() throws Exception {
        CommunityPrefillResponse prefill = service.prefill(12L);

        assertEquals(12L, prefill.getJobId());
        assertEquals("Backend Engineer", prefill.getTitle());
        assertEquals("Northwind", prefill.getCompany());
        assertEquals("Bengaluru", prefill.getLocation());
        assertEquals("Build APIs", prefill.getDescription());
        assertEquals(POSTING, prefill.getSourceUrl());
        assertEquals(CommunityMatchOutcome.NO_COMMUNITY, prefill.getMatch().getOutcome());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(prefill);
        assertFalse(json.contains("999999"));
        assertFalse(json.contains("call the recruiter"));
        assertFalse(json.contains("secret@example.com"));
        assertFalse(json.contains("hashed"));
        verify(communityRepository, never()).saveAndFlush(any());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void anotherUsersJob_isNotFound() {
        when(jobRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        JobNotFoundException ex = assertThrows(JobNotFoundException.class, () -> service.prefill(99L));

        assertEquals("Job not found with id: 99", ex.getMessage());
        verify(communityRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_persistsCommunityAliasesAndCreatorMembership() {
        when(communityRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Community community = invocation.getArgument(0);
            community.setId(100L);
            return community;
        });
        when(communityUrlAliasRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityRepository.findById(100L)).thenAnswer(invocation -> {
            Community community = new Community();
            community.setId(100L);
            community.setCommunityName("Backend Engineer at Northwind");
            community.setTitle("Backend Engineer");
            community.setCompany("Northwind");
            community.setDescription("Build APIs");
            return Optional.of(community);
        });
        when(communityMembershipRepository.findByCommunityIdAndUserId(100L, 7L)).thenReturn(Optional.of(activeMembership()));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(100L, MembershipStatus.ACTIVE))
                .thenReturn(1L);

        CommunityCreateResponse response = service.create(request(false));

        assertEquals(CommunityMatchOutcome.CREATED, response.getOutcome());
        assertEquals("Backend Engineer at Northwind", response.getCommunity().getCommunityName());
        assertEquals(CommunityMembershipView.MEMBER, response.getCommunity().getMembership());
        assertFalse(response.isDescriptionRefined());
        verify(aiService, never()).refineCommunityDescription(any());
        verify(jobRepository, never()).save(any());
        verify(communityMembershipRepository).saveAndFlush(any());
    }

    @Test
    void knownAlias_returnsExistingCommunityWithoutJoining() {
        Community existing = new Community();
        existing.setId(44L);
        existing.setCommunityName("Backend Engineer at Northwind");
        existing.setTitle("Backend Engineer");
        existing.setCompany("Northwind");
        existing.setDescription("Shared");
        CommunityUrlAlias alias = new CommunityUrlAlias();
        alias.setCommunity(existing);
        when(communityUrlAliasRepository.findByNormalizedUrlHash(any())).thenReturn(Optional.of(alias));
        when(communityRepository.findById(44L)).thenReturn(Optional.of(existing));
        when(communityMembershipRepository.findByCommunityIdAndUserId(44L, 7L)).thenReturn(Optional.empty());
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(44L, MembershipStatus.ACTIVE))
                .thenReturn(3L);

        CommunityCreateResponse response = service.create(request(false));

        assertEquals(CommunityMatchOutcome.EXISTING_COMMUNITY, response.getOutcome());
        assertEquals(CommunityMembershipView.NOT_A_MEMBER, response.getCommunity().getMembership());
        assertEquals(3L, response.getCommunity().getActiveMemberCount());
        verify(communityRepository, never()).saveAndFlush(any());
        verify(communityMembershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void uncertainUrl_doesNotCreate() {
        CreateCommunityRequest uncertain = request(false);
        uncertain.setSourceUrl("https://example.com/jobs");

        assertThrows(CommunityMatchUncertainException.class, () -> service.create(uncertain));
        verify(communityRepository, never()).saveAndFlush(any());
    }

    @Test
    void conflictingAliases_doNotCreate() {
        String first = "https://jobs.example.com/openings/one";
        String second = "https://jobs.example.com/openings/two";
        UrlNormalizationUtil urls = new UrlNormalizationUtil();
        Community firstCommunity = new Community();
        firstCommunity.setId(1L);
        Community secondCommunity = new Community();
        secondCommunity.setId(2L);
        when(communityUrlAliasRepository.findByNormalizedUrlHash(urls.sha256Hex(urls.normalizeStrict(first))))
                .thenReturn(Optional.of(aliasFor(firstCommunity)));
        when(communityUrlAliasRepository.findByNormalizedUrlHash(urls.sha256Hex(urls.normalizeStrict(second))))
                .thenReturn(Optional.of(aliasFor(secondCommunity)));
        CreateCommunityRequest conflict = request(false);
        conflict.setSourceUrl(first);
        conflict.setAlternativeUrls(List.of(second));

        assertThrows(CommunityAliasConflictException.class, () -> service.create(conflict));
        verify(communityRepository, never()).saveAndFlush(any());
    }

    @Test
    void refinementFailure_stillCreatesWithTheSubmittedDescription() {
        when(aiService.refineCommunityDescription("Build APIs"))
                .thenThrow(new AiServiceException("unavailable"));
        when(communityRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Community community = invocation.getArgument(0);
            assertEquals("Build APIs", community.getDescription());
            community.setId(100L);
            return community;
        });
        when(communityUrlAliasRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityRepository.findById(100L)).thenReturn(Optional.of(savedCommunity()));
        when(communityMembershipRepository.findByCommunityIdAndUserId(100L, 7L)).thenReturn(Optional.of(activeMembership()));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(eq(100L), eq(MembershipStatus.ACTIVE)))
                .thenReturn(1L);

        CommunityCreateResponse response = service.create(request(true));

        assertTrue(response.getOutcome() == CommunityMatchOutcome.CREATED);
        assertFalse(response.isDescriptionRefined());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void concurrentCreates_keepOneCommunityAndDoNotJoinTheLoser() throws Exception {
        ConcurrentHashMap<String, Community> byKey = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, CommunityMembership> members = new ConcurrentHashMap<>();
        AtomicLong ids = new AtomicLong(1);
        when(communityRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Community community = invocation.getArgument(0);
            community.setId(ids.getAndIncrement());
            Community raced = byKey.putIfAbsent(community.getCanonicalPostingKey(), community);
            if (raced != null) {
                throw new DataIntegrityViolationException("Duplicate uk_community_canonical_posting_key");
            }
            return community;
        });
        when(communityRepository.findByCanonicalPostingKey(any()))
                .thenAnswer(invocation -> Optional.ofNullable(byKey.get(invocation.getArgument(0))));
        when(communityRepository.findById(any())).thenAnswer(invocation -> byKey.values().stream()
                .filter(community -> invocation.getArgument(0).equals(community.getId()))
                .findFirst());
        when(communityUrlAliasRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CommunityMembership membership = invocation.getArgument(0);
            members.put(membership.getUser().getId() + ":" + membership.getCommunity().getId(), membership);
            return membership;
        });
        when(communityMembershipRepository.findByCommunityIdAndUserId(anyLong(), anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(members.get(invocation.getArgument(1) + ":" + invocation.getArgument(0))));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(anyLong(), eq(MembershipStatus.ACTIVE)))
                .thenAnswer(invocation -> members.values().stream()
                        .filter(membership -> invocation.getArgument(0).equals(membership.getCommunity().getId()))
                        .count());
        ThreadLocal<User> active = new ThreadLocal<>();
        when(currentUserService.getCurrentUser()).thenAnswer(invocation -> active.get());
        when(jobRepository.findByIdAndUserId(anyLong(), anyLong())).thenAnswer(invocation -> Optional.of(job));

        User second = new User();
        second.setId(8L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CommunityCreateResponse>> futures = new ArrayList<>();
        for (User caller : List.of(user, second)) {
            futures.add(pool.submit(() -> {
                active.set(caller);
                start.await();
                return service.create(request(false));
            }));
        }
        start.countDown();
        List<CommunityCreateResponse> responses = new ArrayList<>();
        for (Future<CommunityCreateResponse> future : futures) {
            responses.add(future.get(10, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertEquals(1, byKey.size());
        assertEquals(1, members.size());
        assertEquals(1, responses.stream().filter(response -> response.getOutcome() == CommunityMatchOutcome.CREATED).count());
        assertEquals(1, responses.stream().filter(response -> response.getOutcome() == CommunityMatchOutcome.EXISTING_COMMUNITY).count());
        assertTrue(responses.stream()
                .filter(response -> response.getOutcome() == CommunityMatchOutcome.EXISTING_COMMUNITY)
                .allMatch(response -> response.getCommunity().getMembership() == CommunityMembershipView.NOT_A_MEMBER));
        verify(communityMembershipRepository, times(1)).saveAndFlush(any());
    }

    private CreateCommunityRequest request(boolean refine) {
        CreateCommunityRequest request = new CreateCommunityRequest();
        request.setJobId(12L);
        request.setTitle("Backend Engineer");
        request.setCompany("Northwind");
        request.setLocation("Bengaluru");
        request.setDescription("Build APIs");
        request.setSourceUrl(POSTING);
        request.setRefineDescription(refine);
        return request;
    }

    private static CommunityMembership activeMembership() {
        CommunityMembership membership = new CommunityMembership();
        membership.setMembershipStatus(MembershipStatus.ACTIVE);
        return membership;
    }

    private static Community savedCommunity() {
        Community community = new Community();
        community.setId(100L);
        community.setCommunityName("Backend Engineer at Northwind");
        community.setTitle("Backend Engineer");
        community.setCompany("Northwind");
        community.setDescription("Build APIs");
        return community;
    }

    private static CommunityUrlAlias aliasFor(Community community) {
        CommunityUrlAlias alias = new CommunityUrlAlias();
        alias.setCommunity(community);
        return alias;
    }
}
