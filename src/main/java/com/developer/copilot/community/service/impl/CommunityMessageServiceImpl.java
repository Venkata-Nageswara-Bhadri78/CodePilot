package com.developer.copilot.community.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.community.config.CommunityAccountProperties;
import com.developer.copilot.community.dto.response.CommunityMessagePageResponse;
import com.developer.copilot.community.dto.response.CommunityMessageResponse;
import com.developer.copilot.community.dto.response.CommunityMessageSenderResponse;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.CommunityMessage;
import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.repository.CommunityMessageRepository;
import com.developer.copilot.community.service.CommunityMembershipService;
import com.developer.copilot.community.service.CommunityMessageCursor;
import com.developer.copilot.community.service.CommunityMessageNotifier;
import com.developer.copilot.community.service.CommunityMessageService;
import com.developer.copilot.community.service.CommunityMessages;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityMessageServiceImpl implements CommunityMessageService {

    private final CommunityMembershipService communityMembershipService;
    private final CommunityMessageRepository communityMessageRepository;
    private final CommunityMessageNotifier communityMessageNotifier;
    private final CommunityAccountProperties communityAccountProperties;

    @Override
    @Transactional
    public CommunityMessageResponse send(Long communityId, String content) {
        CommunityMembership membership = communityMembershipService.requireActiveMembership(communityId);
        String text = normalize(content);
        CommunityMessage message = new CommunityMessage();
        message.setCommunity(membership.getCommunity());
        message.setSender(membership.getUser());
        message.setContent(text);
        CommunityMessage saved = communityMessageRepository.saveAndFlush(message);
        if (saved.getId() == null || saved.getCreatedAt() == null) {
            throw new IllegalStateException("Community message was not stored with an id and timestamp.");
        }
        CommunityMessageResponse response = toResponse(saved);
        communityMessageNotifier.afterPersist(response);
        log.info("Persisted community message id={} communityId={} userId={}",
                saved.getId(), communityId, membership.getUser().getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityMessagePageResponse history(Long communityId, Integer limit, String cursor) {
        communityMembershipService.requireActiveMembership(communityId);
        int pageSize = resolveLimit(limit);
        Pageable page = PageRequest.of(0, pageSize + 1);
        List<CommunityMessage> newestFirst;
        if (cursor == null || cursor.isBlank()) {
            newestFirst = communityMessageRepository.findByCommunityIdOrderByCreatedAtDescIdDesc(communityId, page);
        } else {
            CommunityMessageCursor.Position position = CommunityMessageCursor.decode(cursor);
            newestFirst = communityMessageRepository.findOlderThan(
                    communityId, position.createdAt(), position.messageId(), page);
        }
        return toPage(communityId, newestFirst, pageSize);
    }

    private String normalize(String content) {
        if (content == null) {
            throw new CommunityValidationException(CommunityMessages.MESSAGE_REQUIRED);
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            throw new CommunityValidationException(CommunityMessages.MESSAGE_REQUIRED);
        }
        if (trimmed.indexOf('\0') >= 0) {
            throw new CommunityValidationException(CommunityMessages.MESSAGE_INVALID);
        }
        int max = communityAccountProperties.getMaxMessageLength();
        if (trimmed.codePointCount(0, trimmed.length()) > max) {
            throw new CommunityValidationException(CommunityMessages.messageTooLong(max));
        }
        return trimmed;
    }

    private int resolveLimit(Integer limit) {
        int max = communityAccountProperties.getMaxMessagePageSize();
        if (limit == null) {
            return communityAccountProperties.getMessagePageSize();
        }
        if (limit < 1 || limit > max) {
            throw new CommunityValidationException(CommunityMessages.invalidPageSize(max));
        }
        return limit;
    }

    private CommunityMessagePageResponse toPage(Long communityId, List<CommunityMessage> newestFirst, int pageSize) {
        boolean hasMore = newestFirst.size() > pageSize;
        int kept = hasMore ? pageSize : newestFirst.size();
        List<CommunityMessageResponse> chronological = new ArrayList<>(kept);
        for (int index = kept - 1; index >= 0; index--) {
            chronological.add(toResponse(newestFirst.get(index)));
        }
        String nextCursor = null;
        if (hasMore && !chronological.isEmpty()) {
            CommunityMessageResponse oldest = chronological.get(0);
            nextCursor = CommunityMessageCursor.encode(oldest.getCreatedAt(), oldest.getId());
        }
        return CommunityMessagePageResponse.builder()
                .communityId(communityId)
                .messages(List.copyOf(chronological))
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    private static CommunityMessageResponse toResponse(CommunityMessage message) {
        User sender = message.getSender();
        Long communityId = message.getCommunity() == null ? null : message.getCommunity().getId();
        return CommunityMessageResponse.builder()
                .id(message.getId())
                .communityId(communityId)
                .sender(CommunityMessageSenderResponse.builder()
                        .userId(sender.getId())
                        .username(sender.getUsername())
                        .fullName(sender.getFullName())
                        .build())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
