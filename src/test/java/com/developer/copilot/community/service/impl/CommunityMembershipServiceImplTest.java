package com.developer.copilot.community.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.community.dto.CommunityMembershipOutcome;
import com.developer.copilot.community.dto.CommunityMembershipView;
import com.developer.copilot.community.dto.response.CommunityListResponse;
import com.developer.copilot.community.dto.response.CommunityMemberResponse;
import com.developer.copilot.community.dto.response.CommunityMembersResponse;
import com.developer.copilot.community.dto.response.CommunityMembershipResponse;
import com.developer.copilot.community.dto.response.CommunitySummaryResponse;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityAccount;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.exception.CommunityAccountRequiredException;
import com.developer.copilot.community.exception.CommunityMembershipRequiredException;
import com.developer.copilot.community.exception.CommunityUnavailableException;
import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.repository.CommunityMembershipRepository;
import com.developer.copilot.community.repository.CommunityRepository;
import com.developer.copilot.community.service.CommunityAccountService;
import com.developer.copilot.community.service.CommunityMembershipWriter;
import com.developer.copilot.community.service.CommunityMessages;
import com.developer.copilot.community.service.CommunitySummaryMapper;

@ExtendWith(MockitoExtension.class)
class CommunityMembershipServiceImplTest {

    @Mock
    private CommunityAccountService communityAccountService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMembershipRepository communityMembershipRepository;

    private CommunityMembershipServiceImpl service;
    private User user;
    private Community community;

    @BeforeEach
    void setUp() {
        CommunityMembershipWriter writer = new CommunityMembershipWriter(communityMembershipRepository);
        service = new CommunityMembershipServiceImpl(
                communityAccountService,
                currentUserService,
                communityRepository,
                communityMembershipRepository,
                writer,
                new CommunitySummaryMapper(communityMembershipRepository));
        user = person(7L, "ada", "Ada Lovelace");
        community = community(10L, user);
        lenient().when(communityAccountService.requireActiveAccount()).thenReturn(new CommunityAccount());
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(communityRepository.findWithCreatorById(10L)).thenReturn(Optional.of(community));
    }

    @Test
    void join_createsOneActiveMembership() {
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.empty());
        when(communityMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CommunityMembership membership = invocation.getArgument(0);
            membership.setId(50L);
            return membership;
        });
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(1L);

        CommunityMembershipResponse response = service.join(10L);

        assertEquals(CommunityMembershipOutcome.JOINED, response.getOutcome());
        assertEquals(CommunityMessages.JOINED, response.getMessage());
        assertEquals(CommunityMembershipView.MEMBER, response.getMembership());
        assertEquals(MembershipStatus.ACTIVE, response.getMembershipStatus());
        assertEquals(10L, response.getCommunityId());
        assertEquals(7L, user.getId());
        assertTrue(response.isCreator());
        assertNotNull(response.getJoinedAt());
        assertNull(response.getLeftAt());
        assertEquals(1L, response.getActiveMemberCount());
        verify(communityRepository, never()).save(any());
        verify(communityRepository, never()).delete(any());
        verify(communityMembershipRepository, never()).delete(any());
    }

    @Test
    void repeatedJoin_isIdempotent() {
        CommunityMembership existing = activeMembership(user, community);
        existing.setId(50L);
        existing.setJoinedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.of(existing));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(1L);

        CommunityMembershipResponse response = service.join(10L);

        assertEquals(CommunityMembershipOutcome.ALREADY_MEMBER, response.getOutcome());
        assertEquals(CommunityMessages.ALREADY_MEMBER, response.getMessage());
        assertEquals(existing.getJoinedAt(), response.getJoinedAt());
        assertEquals(MembershipStatus.ACTIVE, existing.getMembershipStatus());
        verify(communityMembershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateJoin_returnsTheExistingMembership() {
        CommunityMembership existing = activeMembership(user, community);
        existing.setId(50L);
        existing.setJoinedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(communityMembershipRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key uk_community_membership_community_user"));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(1L);

        CommunityMembershipResponse response = service.join(10L);

        assertEquals(CommunityMembershipOutcome.ALREADY_MEMBER, response.getOutcome());
        assertEquals(existing.getJoinedAt(), response.getJoinedAt());
        assertEquals(1L, response.getActiveMemberCount());
    }

    @Test
    void unrelatedConstraint_isNotTreatedAsMembership() {
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.empty());
        when(communityMembershipRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("fk_community_membership_user"));

        assertThrows(DataIntegrityViolationException.class, () -> service.join(10L));
    }

    @Test
    void leave_marksTheRowAndKeepsTheCommunity() {
        CommunityMembership existing = activeMembership(user, community);
        existing.setId(50L);
        LocalDateTime joinedAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        existing.setJoinedAt(joinedAt);
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.of(existing));
        when(communityMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(0L);

        CommunityMembershipResponse response = service.leave(10L);

        assertEquals(CommunityMembershipOutcome.LEFT, response.getOutcome());
        assertEquals(CommunityMessages.LEFT, response.getMessage());
        assertEquals(CommunityMembershipView.NOT_A_MEMBER, response.getMembership());
        assertEquals(MembershipStatus.LEFT, existing.getMembershipStatus());
        assertEquals(joinedAt, existing.getJoinedAt());
        assertNotNull(existing.getLeftAt());
        assertEquals(0L, response.getActiveMemberCount());
        assertTrue(response.isCreator());
        assertEquals("Backend Engineer at Northwind", community.getCommunityName());
        assertEquals(user, community.getCreatedBy());
        verify(communityRepository, never()).delete(any());
        verify(communityMembershipRepository, never()).delete(any());
    }

    @Test
    void repeatedLeave_keepsTheFirstLeaveTime() {
        CommunityMembership existing = activeMembership(user, community);
        existing.setJoinedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.of(existing));
        when(communityMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(0L);

        service.leave(10L);
        LocalDateTime leftAt = existing.getLeftAt();
        CommunityMembershipResponse again = service.leave(10L);

        assertEquals(CommunityMembershipOutcome.ALREADY_LEFT, again.getOutcome());
        assertEquals(CommunityMessages.ALREADY_LEFT, again.getMessage());
        assertEquals(leftAt, again.getLeftAt());
        verify(communityMembershipRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void leave_whenNeverJoined_doesNotInsertARow() {
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.empty());
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(2L);

        CommunityMembershipResponse response = service.leave(10L);

        assertEquals(CommunityMembershipOutcome.NOT_A_MEMBER, response.getOutcome());
        assertEquals(CommunityMembershipView.NOT_A_MEMBER, response.getMembership());
        assertNull(response.getMembershipStatus());
        assertTrue(response.isCreator());
        verify(communityMembershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejoin_updatesTheSameRow() {
        CommunityMembership existing = activeMembership(user, community);
        existing.setId(50L);
        existing.setMembershipStatus(MembershipStatus.LEFT);
        existing.setJoinedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        existing.setLeftAt(LocalDateTime.of(2020, 2, 1, 0, 0));
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.of(existing));
        when(communityMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(1L);

        CommunityMembershipResponse response = service.join(10L);

        assertEquals(CommunityMembershipOutcome.REJOINED, response.getOutcome());
        assertEquals(CommunityMessages.REJOINED, response.getMessage());
        assertEquals(50L, existing.getId());
        assertEquals(MembershipStatus.ACTIVE, existing.getMembershipStatus());
        assertNull(existing.getLeftAt());
        assertTrue(existing.getJoinedAt().isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)));
        assertEquals(CommunityMembershipView.MEMBER, response.getMembership());
    }

    @Test
    void creatorLeave_keepsTheCommunityUsableForAnotherMember() {
        User other = person(8L, "grace", "Grace Hopper");
        other.setEmail("hidden@example.com");
        CommunityMembership creatorRow = activeMembership(user, community);
        creatorRow.setId(1L);
        creatorRow.setJoinedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        CommunityMembership otherRow = activeMembership(other, community);
        otherRow.setId(2L);
        otherRow.setJoinedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
        AtomicReference<User> current = new AtomicReference<>(user);
        when(currentUserService.getCurrentUser()).thenAnswer(invocation -> current.get());
        when(communityMembershipRepository.findByCommunityIdAndUserId(eq(10L), anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(1);
            if (userId.equals(7L)) {
                return Optional.of(creatorRow);
            }
            if (userId.equals(8L)) {
                return Optional.of(otherRow);
            }
            return Optional.empty();
        });
        when(communityMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenAnswer(invocation -> List.of(creatorRow, otherRow).stream()
                        .filter(row -> row.getMembershipStatus() == MembershipStatus.ACTIVE)
                        .count());
        when(communityMembershipRepository.findByCommunityIdAndMembershipStatusOrderByJoinedAtAscIdAsc(
                10L, MembershipStatus.ACTIVE)).thenAnswer(invocation -> List.of(creatorRow, otherRow).stream()
                .filter(row -> row.getMembershipStatus() == MembershipStatus.ACTIVE)
                .toList());

        CommunityMembershipResponse left = service.leave(10L);

        assertEquals(CommunityMembershipOutcome.LEFT, left.getOutcome());
        assertTrue(left.isCreator());
        assertEquals(user, community.getCreatedBy());
        assertEquals(1L, left.getActiveMemberCount());
        assertThrows(CommunityMembershipRequiredException.class, () -> service.details(10L));
        assertThrows(CommunityMembershipRequiredException.class, () -> service.members(10L));
        assertThrows(CommunityMembershipRequiredException.class, () -> service.requireActiveMembership(10L));

        current.set(other);
        CommunitySummaryResponse details = service.details(10L);
        CommunityMembersResponse members = service.members(10L);

        assertEquals(CommunityMembershipView.MEMBER, details.getMembership());
        assertEquals("Build APIs", details.getDescription());
        assertEquals(1, members.getMembers().size());
        CommunityMemberResponse listed = members.getMembers().get(0);
        assertEquals(8L, listed.getUserId());
        assertEquals("grace", listed.getUsername());
        assertEquals("Grace Hopper", listed.getFullName());
        assertEquals(1L, members.getActiveMemberCount());
        assertFalse(memberFields().contains("email"));
        verify(communityRepository, never()).delete(any());
    }

    @Test
    void departedMember_cannotReadMemberOnlyDetails() {
        CommunityMembership existing = activeMembership(user, community);
        existing.setMembershipStatus(MembershipStatus.LEFT);
        existing.setLeftAt(LocalDateTime.of(2026, 3, 1, 0, 0));
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.of(existing));

        assertThrows(CommunityMembershipRequiredException.class, () -> service.details(10L));
        assertThrows(CommunityMembershipRequiredException.class, () -> service.members(10L));
        verify(communityMembershipRepository, never())
                .findByCommunityIdAndMembershipStatusOrderByJoinedAtAscIdAsc(anyLong(), any());
    }

    @Test
    void status_distinguishesMemberLeftAndStranger() {
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(3L);
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.empty());

        CommunityMembershipResponse stranger = service.status(10L);
        assertEquals(CommunityMembershipOutcome.NOT_A_MEMBER, stranger.getOutcome());
        assertEquals(CommunityMembershipView.NOT_A_MEMBER, stranger.getMembership());
        assertNull(stranger.getMembershipStatus());
        assertTrue(stranger.isCreator());

        CommunityMembership left = activeMembership(user, community);
        left.setMembershipStatus(MembershipStatus.LEFT);
        left.setLeftAt(LocalDateTime.of(2026, 4, 1, 0, 0));
        left.setJoinedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.of(left));
        CommunityMembershipResponse departed = service.status(10L);
        assertEquals(CommunityMembershipOutcome.ALREADY_LEFT, departed.getOutcome());
        assertEquals(MembershipStatus.LEFT, departed.getMembershipStatus());

        left.setMembershipStatus(MembershipStatus.ACTIVE);
        left.setLeftAt(null);
        CommunityMembershipResponse member = service.status(10L);
        assertEquals(CommunityMembershipOutcome.MEMBER, member.getOutcome());
        assertEquals(CommunityMembershipView.MEMBER, member.getMembership());
    }

    @Test
    void listMine_returnsEveryActiveCommunity() {
        Community second = community(11L, user);
        second.setCommunityName("Analyst at Contoso");
        second.setTitle("Analyst");
        CommunityMembership first = activeMembership(user, community);
        first.setJoinedAt(LocalDateTime.of(2026, 5, 1, 0, 0));
        CommunityMembership later = activeMembership(user, second);
        later.setJoinedAt(LocalDateTime.of(2026, 6, 1, 0, 0));
        when(communityMembershipRepository.findByUserIdAndMembershipStatusOrderByJoinedAtDescIdDesc(
                7L, MembershipStatus.ACTIVE)).thenReturn(List.of(later, first));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(11L, MembershipStatus.ACTIVE))
                .thenReturn(4L);
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(2L);

        CommunityListResponse response = service.listMine();

        assertEquals(2, response.getCommunities().size());
        assertEquals(11L, response.getCommunities().get(0).getId());
        assertEquals(4L, response.getCommunities().get(0).getActiveMemberCount());
        assertEquals(CommunityMembershipView.MEMBER, response.getCommunities().get(0).getMembership());
        assertEquals(10L, response.getCommunities().get(1).getId());
        assertEquals(2L, response.getCommunities().get(1).getActiveMemberCount());
        assertEquals("Build APIs", response.getCommunities().get(0).getDescription());
        assertEquals("Analyst", response.getCommunities().get(0).getTitle());
    }

    @Test
    void members_returnUsernameAndFullNameOnly() {
        user.setEmail("secret@example.com");
        user.setPassword("hashed");
        CommunityMembership existing = activeMembership(user, community);
        existing.setJoinedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.of(existing));
        when(communityMembershipRepository.findByCommunityIdAndMembershipStatusOrderByJoinedAtAscIdAsc(
                10L, MembershipStatus.ACTIVE)).thenReturn(List.of(existing));

        CommunityMembersResponse response = service.members(10L);

        CommunityMemberResponse member = response.getMembers().get(0);
        assertEquals(7L, member.getUserId());
        assertEquals("ada", member.getUsername());
        assertEquals("Ada Lovelace", member.getFullName());
        assertEquals(MembershipStatus.ACTIVE, member.getMembershipStatus());
        assertEquals(1L, response.getActiveMemberCount());
        assertFalse(memberFields().contains("email"));
        assertFalse(memberFields().contains("password"));
    }

    @Test
    void missingCommunity_isUnavailable() {
        when(communityRepository.findWithCreatorById(99L)).thenReturn(Optional.empty());

        assertThrows(CommunityUnavailableException.class, () -> service.join(99L));
        assertThrows(CommunityUnavailableException.class, () -> service.leave(99L));
        assertThrows(CommunityUnavailableException.class, () -> service.details(99L));
        assertThrows(CommunityUnavailableException.class, () -> service.members(99L));
        assertThrows(CommunityUnavailableException.class, () -> service.status(99L));
        verify(communityMembershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidCommunityId_isRejected() {
        assertThrows(CommunityValidationException.class, () -> service.join(0L));
        assertThrows(CommunityValidationException.class, () -> service.details(-1L));
        verify(communityRepository, never()).findWithCreatorById(any());
    }

    @Test
    void accountRequired_blocksMembership() {
        when(communityAccountService.requireActiveAccount())
                .thenThrow(new CommunityAccountRequiredException(CommunityMessages.MEMBERSHIP_REQUIRED));

        assertThrows(CommunityAccountRequiredException.class, () -> service.join(10L));
        verify(communityRepository, never()).findWithCreatorById(any());
    }

    @Test
    void leftMembership_isNotAMemberOnTheSharedSummary() {
        CommunityMembership left = activeMembership(user, community);
        left.setMembershipStatus(MembershipStatus.LEFT);
        left.setLeftAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(communityMembershipRepository.findByCommunityIdAndUserId(10L, 7L)).thenReturn(Optional.of(left));
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(10L, MembershipStatus.ACTIVE))
                .thenReturn(2L);

        CommunitySummaryResponse summary = new CommunitySummaryMapper(communityMembershipRepository)
                .toSummary(community, 7L);

        assertEquals(CommunityMembershipView.NOT_A_MEMBER, summary.getMembership());
        assertEquals(2L, summary.getActiveMemberCount());
    }

    @Test
    void concurrentJoins_keepOneMembership() throws Exception {
        ConcurrentHashMap<String, CommunityMembership> rows = new ConcurrentHashMap<>();
        AtomicLong ids = new AtomicLong(1);
        when(communityMembershipRepository.findByCommunityIdAndUserId(anyLong(), anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(rows.get(invocation.getArgument(0) + ":" + invocation.getArgument(1))));
        when(communityMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CommunityMembership membership = invocation.getArgument(0);
            String key = membership.getCommunity().getId() + ":" + membership.getUser().getId();
            if (membership.getId() == null) {
                membership.setId(ids.getAndIncrement());
                if (rows.putIfAbsent(key, membership) != null) {
                    throw new DataIntegrityViolationException(
                            "Duplicate entry for key uk_community_membership_community_user");
                }
                return membership;
            }
            rows.put(key, membership);
            return membership;
        });
        when(communityMembershipRepository.countByCommunityIdAndMembershipStatus(anyLong(), eq(MembershipStatus.ACTIVE)))
                .thenAnswer(invocation -> rows.values().stream()
                        .filter(membership -> invocation.getArgument(0).equals(membership.getCommunity().getId()))
                        .filter(membership -> membership.getMembershipStatus() == MembershipStatus.ACTIVE)
                        .count());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CommunityMembershipResponse>> futures = List.of(
                pool.submit(() -> {
                    start.await();
                    return service.join(10L);
                }),
                pool.submit(() -> {
                    start.await();
                    return service.join(10L);
                }));
        start.countDown();
        List<CommunityMembershipResponse> responses = List.of(
                futures.get(0).get(10, TimeUnit.SECONDS),
                futures.get(1).get(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, rows.size());
        assertEquals(MembershipStatus.ACTIVE, rows.values().iterator().next().getMembershipStatus());
        assertEquals(1, responses.stream()
                .filter(response -> response.getOutcome() == CommunityMembershipOutcome.JOINED)
                .count());
        assertEquals(1, responses.stream()
                .filter(response -> response.getOutcome() == CommunityMembershipOutcome.ALREADY_MEMBER)
                .count());
    }

    private static List<String> memberFields() {
        return List.of(CommunityMemberResponse.class.getDeclaredFields()).stream()
                .map(field -> field.getName())
                .toList();
    }

    private static User person(Long id, String username, String fullName) {
        User person = new User();
        person.setId(id);
        person.setUsername(username);
        person.setFullName(fullName);
        return person;
    }

    private static Community community(Long id, User creator) {
        Community community = new Community();
        community.setId(id);
        community.setCommunityName("Backend Engineer at Northwind");
        community.setTitle("Backend Engineer");
        community.setCompany("Northwind");
        community.setLocation("Bengaluru");
        community.setDescription("Build APIs");
        community.setCreatedBy(creator);
        return community;
    }

    private static CommunityMembership activeMembership(User member, Community community) {
        CommunityMembership membership = new CommunityMembership();
        membership.setUser(member);
        membership.setCommunity(community);
        membership.setMembershipStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return membership;
    }
}
