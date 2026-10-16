package com.developer.copilot.community.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.community.config.CommunityAccountProperties;
import com.developer.copilot.community.dto.response.CommunityMessagePageResponse;
import com.developer.copilot.community.dto.response.CommunityMessageResponse;
import com.developer.copilot.community.dto.response.CommunityMessageSenderResponse;
import com.developer.copilot.community.entity.Community;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.CommunityMessage;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.exception.CommunityMembershipRequiredException;
import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.repository.CommunityMessageRepository;
import com.developer.copilot.community.service.CommunityMembershipService;
import com.developer.copilot.community.service.CommunityMessageCursor;
import com.developer.copilot.community.service.CommunityMessageNotifier;
import com.developer.copilot.community.service.CommunityMessages;

@ExtendWith(MockitoExtension.class)
class CommunityMessageServiceImplTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 16, 13, 0);

    @Mock
    private CommunityMembershipService communityMembershipService;
    @Mock
    private CommunityMessageRepository communityMessageRepository;
    @Mock
    private CommunityMessageNotifier communityMessageNotifier;

    private CommunityMessageServiceImpl service;
    private User user;
    private Community community;
    private CommunityMembership membership;

    @BeforeEach
    void setUp() {
        service = new CommunityMessageServiceImpl(
                communityMembershipService,
                communityMessageRepository,
                communityMessageNotifier,
                new CommunityAccountProperties());
        user = new User();
        user.setId(7L);
        user.setUsername("ada");
        user.setFullName("Ada Lovelace");
        user.setEmail("secret@example.com");
        user.setPassword("hashed");
        community = new Community();
        community.setId(10L);
        membership = new CommunityMembership();
        membership.setCommunity(community);
        membership.setUser(user);
        membership.setMembershipStatus(MembershipStatus.ACTIVE);
    }

    @Test
    void send_persistsTheAuthenticatedSenderAndThenNotifies() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);
        when(communityMessageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CommunityMessage message = invocation.getArgument(0);
            message.setId(42L);
            message.setCreatedAt(START);
            return message;
        });

        CommunityMessageResponse response = service.send(10L, "  Hello 😀  ");

        ArgumentCaptor<CommunityMessage> saved = ArgumentCaptor.forClass(CommunityMessage.class);
        InOrder order = inOrder(communityMessageRepository, communityMessageNotifier);
        order.verify(communityMessageRepository).saveAndFlush(saved.capture());
        order.verify(communityMessageNotifier).afterPersist(response);
        assertEquals(user, saved.getValue().getSender());
        assertEquals(community, saved.getValue().getCommunity());
        assertEquals("Hello 😀", saved.getValue().getContent());
        assertEquals(42L, response.getId());
        assertEquals(START, response.getCreatedAt());
        assertEquals(7L, response.getSender().getUserId());
        assertEquals("ada", response.getSender().getUsername());
        assertEquals("Ada Lovelace", response.getSender().getFullName());
        assertFalse(senderFields().contains("email"));
        assertFalse(senderFields().contains("password"));
    }

    @Test
    void send_rejectsBlankOversizedAndInvalidText() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);

        assertEquals(CommunityMessages.MESSAGE_REQUIRED,
                assertThrows(CommunityValidationException.class, () -> service.send(10L, "  \n")).getMessage());
        assertEquals(CommunityMessages.MESSAGE_INVALID,
                assertThrows(CommunityValidationException.class, () -> service.send(10L, "hi\0there")).getMessage());
        assertEquals(CommunityMessages.messageTooLong(4000),
                assertThrows(CommunityValidationException.class, () -> service.send(10L, "a".repeat(4001))).getMessage());
        verify(communityMessageRepository, never()).saveAndFlush(any());
        verify(communityMessageNotifier, never()).afterPersist(any());
    }

    @Test
    void send_allowsEmojiWithinTheCodePointLimit() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);
        when(communityMessageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CommunityMessage message = invocation.getArgument(0);
            message.setId(1L);
            message.setCreatedAt(START);
            return message;
        });
        String emoji = "😀".repeat(2001);

        CommunityMessageResponse response = service.send(10L, emoji);

        assertEquals(emoji, response.getContent());
        assertTrue(emoji.length() > emoji.codePointCount(0, emoji.length()));
    }

    @Test
    void send_keepsNewlines() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);
        when(communityMessageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CommunityMessage message = invocation.getArgument(0);
            message.setId(1L);
            message.setCreatedAt(START);
            return message;
        });

        CommunityMessageResponse response = service.send(10L, "first line\nsecond line");

        assertEquals("first line\nsecond line", response.getContent());
    }

    @Test
    void nonMember_cannotSendOrRead() {
        when(communityMembershipService.requireActiveMembership(10L))
                .thenThrow(new CommunityMembershipRequiredException(CommunityMessages.MEMBERSHIP_REQUIRED));

        assertThrows(CommunityMembershipRequiredException.class, () -> service.send(10L, "hello"));
        assertThrows(CommunityMembershipRequiredException.class, () -> service.history(10L, null, null));
        verify(communityMessageRepository, never()).saveAndFlush(any());
        verify(communityMessageRepository, never()).findByCommunityIdOrderByCreatedAtDescIdDesc(anyLong(), any());
        verify(communityMessageNotifier, never()).afterPersist(any());
    }

    @Test
    void databaseFailure_doesNotReportSuccessOrNotify() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);
        when(communityMessageRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("community_messages insert failed"));

        assertThrows(DataIntegrityViolationException.class, () -> service.send(10L, "hello"));
        verify(communityMessageNotifier, never()).afterPersist(any());
    }

    @Test
    void missingServerIdentity_isNotASuccessfulSend() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);
        when(communityMessageRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(IllegalStateException.class, () -> service.send(10L, "hello"));
        verify(communityMessageNotifier, never()).afterPersist(any());
    }

    @Test
    void history_returnsTheNewestPageOldestFirstAndACursor() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);
        List<CommunityMessage> store = new ArrayList<>();
        for (int id = 1; id <= 5; id++) {
            store.add(stored(id, START.plusSeconds(id), "m" + id));
        }
        stubHistory(store);

        CommunityMessagePageResponse page = service.history(10L, 3, null);

        assertEquals(List.of(3L, 4L, 5L), page.getMessages().stream().map(CommunityMessageResponse::getId).toList());
        assertEquals(List.of("m3", "m4", "m5"), page.getMessages().stream().map(CommunityMessageResponse::getContent).toList());
        assertTrue(page.isHasMore());
        assertEquals(CommunityMessageCursor.encode(START.plusSeconds(3), 3L), page.getNextCursor());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(communityMessageRepository).findByCommunityIdOrderByCreatedAtDescIdDesc(eq(10L), pageable.capture());
        assertEquals(4, pageable.getValue().getPageSize());
        assertTrue(pageable.getValue().getSort().isUnsorted());
    }

    @Test
    void history_pagesOlderMessagesWithoutDuplicatesOrGaps() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);
        List<CommunityMessage> store = new ArrayList<>();
        for (int id = 1; id <= 10; id++) {
            store.add(stored(id, START.plusSeconds(id), "m" + id));
        }
        stubHistory(store);

        List<Long> seen = walk(5);
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), seen);
    }

    @Test
    void history_ordersEqualTimestampsById() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);
        List<CommunityMessage> store = List.of(
                stored(1, START, "a"),
                stored(2, START, "b"),
                stored(3, START, "c"));
        stubHistory(store);

        CommunityMessagePageResponse first = service.history(10L, 2, null);
        CommunityMessagePageResponse older = service.history(10L, 2, first.getNextCursor());

        assertEquals(List.of(2L, 3L), first.getMessages().stream().map(CommunityMessageResponse::getId).toList());
        assertTrue(first.isHasMore());
        assertEquals(List.of(1L), older.getMessages().stream().map(CommunityMessageResponse::getId).toList());
        assertFalse(older.isHasMore());
        assertNull(older.getNextCursor());
    }

    @Test
    void olderPage_ignoresAMessageInsertedAfterTheFirstPage() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);
        CopyOnWriteArrayList<CommunityMessage> store = new CopyOnWriteArrayList<>();
        for (int id = 1; id <= 12; id++) {
            store.add(stored(id, START.plusSeconds(id), "m" + id));
        }
        stubHistory(store);

        CommunityMessagePageResponse first = service.history(10L, 5, "  ");
        store.add(stored(13, START.plusSeconds(100), "new"));
        List<Long> seen = new ArrayList<>(ids(first));
        String cursor = first.getNextCursor();
        while (cursor != null) {
            CommunityMessagePageResponse older = service.history(10L, 5, cursor);
            seen.addAll(0, ids(older));
            cursor = older.getNextCursor();
        }

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L), seen);
        assertEquals(seen.size(), new HashSet<>(seen).size());
        assertFalse(seen.contains(13L));
    }

    @Test
    void history_rejectsABadCursorAndAnOversizedPage() {
        when(communityMembershipService.requireActiveMembership(10L)).thenReturn(membership);

        assertEquals(CommunityMessages.INVALID_CURSOR,
                assertThrows(CommunityValidationException.class, () -> service.history(10L, 10, "nope")).getMessage());
        assertEquals(CommunityMessages.invalidPageSize(50),
                assertThrows(CommunityValidationException.class, () -> service.history(10L, 51, null)).getMessage());
        verify(communityMessageRepository, never()).findOlderThan(anyLong(), any(), anyLong(), any());
    }

    @Test
    void cursor_roundTripsThePosition() {
        CommunityMessageCursor.Position position = CommunityMessageCursor.decode(
                CommunityMessageCursor.encode(START.withNano(123456000), 99L));

        assertEquals(START.withNano(123456000), position.createdAt());
        assertEquals(99L, position.messageId());
        assertThrows(CommunityValidationException.class, () -> CommunityMessageCursor.decode("v1.@@@"));
    }

    private List<Long> walk(int pageSize) {
        List<List<Long>> pages = new ArrayList<>();
        CommunityMessagePageResponse page = service.history(10L, pageSize, null);
        pages.add(ids(page));
        String cursor = page.getNextCursor();
        while (cursor != null) {
            page = service.history(10L, pageSize, cursor);
            pages.add(0, ids(page));
            cursor = page.getNextCursor();
        }
        List<Long> seen = new ArrayList<>();
        pages.forEach(seen::addAll);
        return seen;
    }

    private void stubHistory(List<CommunityMessage> store) {
        lenient().when(communityMessageRepository.findByCommunityIdOrderByCreatedAtDescIdDesc(eq(10L), any()))
                .thenAnswer(invocation -> newest(store, null, null, invocation.getArgument(1)));
        lenient().when(communityMessageRepository.findOlderThan(eq(10L), any(), anyLong(), any()))
                .thenAnswer(invocation -> newest(
                        store,
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)));
    }

    private static List<CommunityMessage> newest(
            List<CommunityMessage> store,
            LocalDateTime createdAt,
            Long messageId,
            Pageable pageable) {
        return store.stream()
                .filter(message -> createdAt == null || isOlder(message, createdAt, messageId))
                .sorted(Comparator.comparing(CommunityMessage::getCreatedAt).thenComparing(CommunityMessage::getId).reversed())
                .limit(pageable.getPageSize())
                .toList();
    }

    private static boolean isOlder(CommunityMessage message, LocalDateTime createdAt, Long messageId) {
        return message.getCreatedAt().isBefore(createdAt)
                || (message.getCreatedAt().equals(createdAt) && message.getId() < messageId);
    }

    private static List<Long> ids(CommunityMessagePageResponse page) {
        return page.getMessages().stream().map(CommunityMessageResponse::getId).toList();
    }

    private CommunityMessage stored(long id, LocalDateTime createdAt, String content) {
        CommunityMessage message = new CommunityMessage();
        message.setId(id);
        message.setCommunity(community);
        message.setSender(user);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        return message;
    }

    private static Set<String> senderFields() {
        Set<String> names = new HashSet<>();
        for (var field : CommunityMessageSenderResponse.class.getDeclaredFields()) {
            names.add(field.getName());
        }
        return names;
    }
}
