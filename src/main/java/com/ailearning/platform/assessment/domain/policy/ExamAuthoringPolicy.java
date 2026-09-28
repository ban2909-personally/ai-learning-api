package com.ailearning.platform.assessment.domain.policy;

import com.ailearning.platform.assessment.domain.enumtype.ExamRevisionStatus;
import com.ailearning.platform.assessment.domain.model.ExamRevision;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.Set;
import java.util.UUID;

public final class ExamAuthoringPolicy {
    private ExamAuthoringPolicy() {}

    public static boolean isPublisher(Set<String> roles) {
        return roles.contains("ADMIN") || roles.contains("LEADER");
    }

    public static void requireStaff(Set<String> roles) {
        require(
                isPublisher(roles) || roles.contains("LECTURE") || roles.contains("INSTRUCTOR"),
                "Tài khoản không có quyền quản lý đề thi.");
    }

    public static void requireAuthor(Set<String> roles) {
        require(
                roles.contains("ADMIN")
                        || roles.contains("LECTURE")
                        || roles.contains("INSTRUCTOR"),
                "Tài khoản không có quyền soạn đề thi.");
    }

    public static void requireRead(UUID actor, Set<String> roles, ExamRevision revision) {
        require(
                roles.contains("ADMIN")
                        || actor.equals(revision.authorId())
                        || roles.contains("LEADER")
                                && revision.status() != ExamRevisionStatus.DRAFT,
                "Không có quyền xem phiên bản đề này.");
    }

    public static void requireEdit(UUID actor, Set<String> roles, ExamRevision revision) {
        requireAuthor(roles);
        require(
                roles.contains("ADMIN") || actor.equals(revision.authorId()),
                "Chỉ tác giả hoặc admin được sửa đề.");
    }

    public static void requirePublisher(Set<String> roles) {
        require(isPublisher(roles), "Chỉ leader hoặc admin được phát hành đề.");
    }

    public static void requireState(
            ExamRevision revision, long expectedVersion, ExamRevisionStatus required) {
        if (expectedVersion < 0
                || revision.version() != expectedVersion
                || revision.status() != required) {
            throw conflict();
        }
    }

    public static BusinessException conflict() {
        return new BusinessException(
                "exam_revision_conflict",
                ErrorType.CONFLICT,
                "Phiên bản hoặc trạng thái đề đã thay đổi. Hãy tải lại trước khi tiếp tục.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException("exam_authoring_forbidden", ErrorType.FORBIDDEN, message);
        }
    }
}
