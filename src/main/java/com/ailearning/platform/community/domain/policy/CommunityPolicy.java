package com.ailearning.platform.community.domain.policy;

import com.ailearning.platform.community.domain.model.MemberRole;
import com.ailearning.platform.community.domain.model.Membership;
import com.ailearning.platform.community.domain.model.Space;
import com.ailearning.platform.community.domain.model.SpaceKind;
import com.ailearning.platform.community.domain.model.SpaceVisibility;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

public class CommunityPolicy {
    public void validateSpace(
            String name, String description, SpaceKind kind, SpaceVisibility visibility) {
        if (name == null
                || name.isBlank()
                || name.trim().length() < 3
                || name.length() > 120
                || description == null
                || description.length() > 2000
                || kind == null
                || visibility == null
                || (kind == SpaceKind.PAGE && visibility != SpaceVisibility.PUBLIC)) {
            throw badRequest(
                    "invalid_space", "Tên cộng đồng cần 3–120 ký tự; page phải công khai.");
        }
    }

    public String postBody(String body, boolean share) {
        if (body == null || body.length() > 5000 || (!share && body.isBlank())) {
            throw badRequest("invalid_post", "Bài viết cần nội dung và không quá 5.000 ký tự.");
        }
        return body.trim();
    }

    public String commentBody(String body) {
        if (body == null || body.isBlank() || body.length() > 2000) {
            throw badRequest("invalid_comment", "Bình luận cần nội dung và không quá 2.000 ký tự.");
        }
        return body.trim();
    }

    public void requireVisible(Space space, Membership member) {
        if (space != null
                && space.visibility() == SpaceVisibility.PRIVATE
                && (member == null || !member.active())) {
            throw denied("private_space", "Chỉ thành viên được xem nội dung nhóm riêng tư.");
        }
    }

    public void requirePosting(Space space, Membership member) {
        if (space == null) return;
        if (space.kind() == SpaceKind.PAGE) {
            requireManager(member);
        } else if (member == null || !member.active()) {
            throw denied("space_membership_required", "Hãy tham gia nhóm trước khi đăng bài.");
        }
    }

    public void requireInteraction(Space space, Membership member) {
        if (space != null
                && space.kind() == SpaceKind.GROUP
                && (member == null || !member.active())) {
            throw denied("space_membership_required", "Hãy tham gia nhóm trước khi tương tác.");
        }
    }

    public void requireManager(Membership member) {
        if (member == null || !member.manager()) {
            throw denied(
                    "space_admin_required",
                    "Chỉ quản trị viên cộng đồng được thực hiện thao tác này.");
        }
    }

    public void requireRemoval(Membership actor, Membership target) {
        requireManager(actor);
        if (target == null
                || target.role() == MemberRole.OWNER
                || (target.role() == MemberRole.ADMIN && actor.role() != MemberRole.OWNER)) {
            throw denied("protected_space_member", "Không thể xóa hoặc từ chối quản trị viên này.");
        }
    }

    public void requireRoleChange(Membership actor, Membership target, MemberRole role) {
        if (actor == null
                || !actor.active()
                || actor.role() != MemberRole.OWNER
                || target == null
                || !target.active()
                || target.role() == MemberRole.OWNER
                || (role != MemberRole.ADMIN && role != MemberRole.MEMBER)) {
            throw denied("space_role_denied", "Chỉ chủ cộng đồng được đổi vai trò của thành viên.");
        }
    }

    public void requireShareable(Space space) {
        if (space != null && space.visibility() == SpaceVisibility.PRIVATE) {
            throw denied("private_post_share", "Không thể chia sẻ bài viết từ nhóm riêng tư.");
        }
    }

    private BusinessException denied(String code, String detail) {
        return new BusinessException(code, ErrorType.FORBIDDEN, detail);
    }

    private BusinessException badRequest(String code, String detail) {
        return new BusinessException(code, ErrorType.BAD_REQUEST, detail);
    }
}
