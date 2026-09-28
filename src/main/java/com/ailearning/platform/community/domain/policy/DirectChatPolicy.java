package com.ailearning.platform.community.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.UUID;

public class DirectChatPolicy {
    public String body(String text) {
        if (text == null || text.isBlank() || text.length() > 2000)
            throw new BusinessException(
                    "invalid_direct_message", ErrorType.BAD_REQUEST, "Tin nhắn cần 1–2.000 ký tự.");
        return text.trim();
    }

    public void requireClientId(UUID clientId) {
        if (clientId == null)
            throw new BusinessException(
                    "invalid_direct_message", ErrorType.BAD_REQUEST, "Thiếu mã gửi tin nhắn.");
    }

    public void requireDifferentUsers(UUID actor, UUID peer) {
        if (actor.equals(peer))
            throw new BusinessException(
                    "invalid_direct_recipient", ErrorType.BAD_REQUEST, "Hãy chọn người dùng khác.");
    }

    public void requireSend(String status) {
        if (!"ACTIVE".equals(status))
            throw new BusinessException(
                    "direct_request_pending",
                    ErrorType.CONFLICT,
                    "Người nhận cần chấp nhận lời mời chat trước khi gửi thêm.");
    }
}
