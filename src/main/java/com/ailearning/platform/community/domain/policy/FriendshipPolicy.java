package com.ailearning.platform.community.domain.policy;

import com.ailearning.platform.community.domain.model.Friendship;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.UUID;

public class FriendshipPolicy {
    public void requireDifferentUsers(UUID actor, UUID peer) {
        if (peer == null || actor.equals(peer))
            throw new BusinessException(
                    "invalid_friendship",
                    ErrorType.BAD_REQUEST,
                    "Không thể kết bạn với chính mình.");
    }

    public void requireRequest(UUID actor, Friendship current) {
        if ("PENDING".equals(current.status()) && !actor.equals(current.initiatorId()))
            throw new BusinessException(
                    "friend_request_received",
                    ErrorType.CONFLICT,
                    "Hãy chấp nhận lời mời kết bạn đã nhận.");
    }

    public void requireAccept(UUID actor, Friendship current) {
        if (!"PENDING".equals(current.status()) || actor.equals(current.initiatorId()))
            throw new BusinessException(
                    "friend_request_denied",
                    ErrorType.FORBIDDEN,
                    "Chỉ người nhận được chấp nhận lời mời đang chờ.");
    }

    public String relationship(UUID viewer, UUID person, Friendship current) {
        if (person.equals(viewer)) return "SELF";
        if (viewer == null || current == null) return "NONE";
        if ("ACCEPTED".equals(current.status())) return "FRIENDS";
        return viewer.equals(current.initiatorId()) ? "OUTGOING" : "INCOMING";
    }
}
