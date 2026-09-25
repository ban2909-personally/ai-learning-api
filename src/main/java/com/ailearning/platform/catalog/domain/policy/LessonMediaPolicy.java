package com.ailearning.platform.catalog.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class LessonMediaPolicy {
    public static final long MAX_UPLOAD_BYTES = 9_999_999L;
    private final long maxUploadBytes;
    private final Set<String> allowedContentTypes;

    public LessonMediaPolicy(long maxUploadBytes, Set<String> allowedContentTypes) {
        this.maxUploadBytes = Math.min(maxUploadBytes, MAX_UPLOAD_BYTES);
        this.allowedContentTypes =
                allowedContentTypes.stream()
                        .map(contentType -> contentType.toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void ensureCanManage(UUID actorId, boolean administrator, UUID instructorId) {
        if (!administrator && !instructorId.equals(actorId)) {
            throw new BusinessException(
                    "course_management_denied",
                    ErrorType.FORBIDDEN,
                    "Bạn không có quyền quản lý nội dung khóa học này.");
        }
    }

    public String validateUpload(String contentType, long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new BusinessException(
                    "empty_media_file", ErrorType.BAD_REQUEST, "Tệp nội dung không được để trống.");
        }
        if (sizeBytes > maxUploadBytes) {
            throw new BusinessException(
                    "media_file_too_large",
                    ErrorType.BAD_REQUEST,
                    "Tệp nội dung phải nhỏ hơn 10 MB.");
        }
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!allowedContentTypes.contains(normalized)) {
            throw new BusinessException(
                    "unsupported_media_type",
                    ErrorType.BAD_REQUEST,
                    "Định dạng nội dung chưa được hỗ trợ.");
        }
        return normalized;
    }
}
