package com.ailearning.platform.community.application.service.impl;

import com.ailearning.platform.community.api.contract.DirectConversationView;
import com.ailearning.platform.community.api.contract.DirectInboxPage;
import com.ailearning.platform.community.api.contract.DirectMessagePage;
import com.ailearning.platform.community.api.contract.DirectMessageView;
import com.ailearning.platform.community.api.usecase.DirectChatUseCase;
import com.ailearning.platform.community.application.port.out.DirectChatStore;
import com.ailearning.platform.community.domain.policy.DirectChatPolicy;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class DirectChatService implements DirectChatUseCase {
    private final DirectChatStore store;
    private final AccountAccess accounts;
    private final DirectChatPolicy policy = new DirectChatPolicy();

    public DirectChatService(DirectChatStore store, AccountAccess accounts) {
        this.store = store;
        this.accounts = accounts;
    }

    private void actor(UUID actor) {
        if (actor == null)
            throw new BusinessException(
                    "login_required", ErrorType.UNAUTHORIZED, "Hãy đăng nhập để chat.");
        accounts.requireActive(actor);
    }

    private DirectConversationView conversation(UUID actor, UUID id) {
        actor(actor);
        return store.conversation(actor, id).orElseThrow(this::missing);
    }

    @Override
    public DirectConversationView start(UUID actor, String email, UUID clientId, String body) {
        actor(actor);
        policy.requireClientId(clientId);
        if (email == null || email.isBlank() || email.length() > 254)
            throw new BusinessException(
                    "invalid_direct_recipient",
                    ErrorType.BAD_REQUEST,
                    "Email người nhận không hợp lệ.");
        UUID peer = accounts.findActiveByEmail(email.trim()).orElseThrow(this::missing).id();
        policy.requireDifferentUsers(actor, peer);
        return store.start(actor, peer, clientId, policy.body(body));
    }

    @Override
    public DirectInboxPage inbox(UUID actor, String filter, int page) {
        actor(actor);
        if (!Set.of("all", "unread", "requests").contains(filter) || page < 0 || page > 1000)
            throw new BusinessException(
                    "invalid_direct_inbox", ErrorType.BAD_REQUEST, "Bộ lọc chat không hợp lệ.");
        return store.inbox(actor, filter, page);
    }

    @Override
    public DirectConversationView startWithPeer(UUID actor, UUID peer, UUID clientId, String body) {
        actor(actor);
        policy.requireDifferentUsers(actor, peer);
        if (peer == null) throw missing();
        accounts.requireActive(peer);
        policy.requireClientId(clientId);
        return store.start(actor, peer, clientId, policy.body(body));
    }

    @Override
    public Optional<DirectConversationView> findWithPeer(UUID actor, UUID peer) {
        actor(actor);
        policy.requireDifferentUsers(actor, peer);
        if (peer == null) throw missing();
        accounts.requireActive(peer);
        return store.findWithPeer(actor, peer);
    }

    @Override
    public DirectMessagePage messages(UUID actor, UUID id, Long after, Long before) {
        conversation(actor, id);
        if ((after != null && before != null)
                || (after != null && after < 0)
                || (before != null && before <= 0))
            throw new BusinessException(
                    "invalid_chat_cursor", ErrorType.BAD_REQUEST, "Cursor chat không hợp lệ.");
        var rows = store.messages(actor, id, after, before, 51);
        var messages = new ArrayList<>(rows.subList(0, Math.min(rows.size(), 50)));
        if (after == null) Collections.reverse(messages);
        return new DirectMessagePage(
                List.copyOf(messages),
                messages.isEmpty() ? 0 : messages.get(0).sequence(),
                messages.isEmpty() ? 0 : messages.get(messages.size() - 1).sequence(),
                rows.size() > 50);
    }

    @Override
    public DirectMessageView send(UUID actor, UUID id, UUID clientId, String body) {
        var conversation = conversation(actor, id);
        accounts.requireActive(conversation.peerId());
        policy.requireSend(conversation.status());
        policy.requireClientId(clientId);
        return store.send(actor, id, clientId, policy.body(body));
    }

    @Override
    public DirectConversationView decide(UUID actor, UUID id, boolean accept) {
        conversation(actor, id);
        if (!store.decide(actor, id, accept))
            throw new BusinessException(
                    "direct_request_denied",
                    ErrorType.FORBIDDEN,
                    "Chỉ người nhận được xử lý lời mời chat đang chờ.");
        return conversation(actor, id);
    }

    @Override
    public void markRead(UUID actor, UUID id, long sequence) {
        conversation(actor, id);
        if (sequence < 0)
            throw new BusinessException(
                    "invalid_chat_cursor", ErrorType.BAD_REQUEST, "Mốc đọc không hợp lệ.");
        if (!store.markRead(actor, id, sequence)) throw missing();
    }

    private BusinessException missing() {
        return new BusinessException(
                "direct_chat_not_found",
                ErrorType.NOT_FOUND,
                "Không tìm thấy người nhận hoặc đoạn chat.");
    }
}
