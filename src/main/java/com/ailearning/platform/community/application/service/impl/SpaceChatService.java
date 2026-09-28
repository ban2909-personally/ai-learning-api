package com.ailearning.platform.community.application.service.impl;

import com.ailearning.platform.community.api.contract.SpaceChatPage;
import com.ailearning.platform.community.api.contract.SpaceMessageView;
import com.ailearning.platform.community.api.usecase.SpaceChatUseCase;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.community.application.port.out.SpaceChatStore;
import com.ailearning.platform.community.domain.policy.CommunityPolicy;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SpaceChatService implements SpaceChatUseCase {
    private final CommunityStore community;
    private final SpaceChatStore chat;
    private final AccountAccess accounts;
    private final CommunityPolicy policy = new CommunityPolicy();

    public SpaceChatService(CommunityStore community, SpaceChatStore chat, AccountAccess accounts) {
        this.community = community;
        this.chat = chat;
        this.accounts = accounts;
    }

    private void authorize(UUID actor, UUID spaceId) {
        if (actor == null)
            throw new BusinessException(
                    "login_required", ErrorType.UNAUTHORIZED, "Hãy đăng nhập để trò chuyện.");
        accounts.requireActive(actor);
        community
                .findSpace(spaceId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "community_not_found",
                                        ErrorType.NOT_FOUND,
                                        "Không tìm thấy cộng đồng."));
        policy.requireChat(community.membership(spaceId, actor).orElse(null));
    }

    @Override
    public SpaceChatPage messages(UUID actor, UUID spaceId, Long after, Long before) {
        authorize(actor, spaceId);
        if ((after != null && before != null)
                || (after != null && after < 0)
                || (before != null && before <= 0)) {
            throw new BusinessException(
                    "invalid_chat_cursor",
                    ErrorType.BAD_REQUEST,
                    "Chỉ dùng một cursor chat hợp lệ.");
        }
        List<SpaceMessageView> rows = chat.messages(actor, spaceId, after, before, 51);
        boolean more = rows.size() > 50;
        List<SpaceMessageView> messages =
                new ArrayList<>(rows.subList(0, Math.min(50, rows.size())));
        if (after == null) Collections.reverse(messages);
        return new SpaceChatPage(
                List.copyOf(messages),
                messages.isEmpty() ? 0 : messages.get(0).sequence(),
                messages.isEmpty() ? 0 : messages.get(messages.size() - 1).sequence(),
                more);
    }

    @Override
    public SpaceMessageView send(UUID actor, UUID spaceId, UUID clientId, String body) {
        authorize(actor, spaceId);
        if (clientId == null)
            throw new BusinessException(
                    "invalid_chat_message", ErrorType.BAD_REQUEST, "Thiếu mã gửi tin nhắn.");
        if (body == null || body.isBlank() || body.length() > 2000)
            throw new BusinessException(
                    "invalid_chat_message", ErrorType.BAD_REQUEST, "Tin nhắn cần 1–2.000 ký tự.");
        return chat.send(actor, spaceId, clientId, body.trim());
    }

    @Override
    public void remove(UUID actor, UUID spaceId, UUID messageId) {
        authorize(actor, spaceId);
        if (!chat.remove(actor, spaceId, messageId))
            throw new BusinessException(
                    "chat_moderation_denied",
                    ErrorType.FORBIDDEN,
                    "Chỉ tác giả hoặc quản trị viên cộng đồng được gỡ tin nhắn.");
    }
}
