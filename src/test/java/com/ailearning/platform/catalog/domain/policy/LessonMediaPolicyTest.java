package com.ailearning.platform.catalog.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LessonMediaPolicyTest {
    private final LessonMediaPolicy policy = new LessonMediaPolicy(
            1_024,
            Set.of("video/mp4", "video/webm")
    );

    @Test
    void permitsOwnerAndAdministrator() {
        UUID ownerId = UUID.randomUUID();

        assertDoesNotThrow(() -> policy.ensureCanManage(ownerId, false, ownerId));
        assertDoesNotThrow(() -> policy.ensureCanManage(UUID.randomUUID(), true, ownerId));
    }

    @Test
    void rejectsAnotherInstructor() {
        BusinessException error = assertThrows(BusinessException.class, () ->
                policy.ensureCanManage(UUID.randomUUID(), false, UUID.randomUUID()));

        assertEquals("course_management_denied", error.code());
    }

    @Test
    void validatesSizeAndNormalizesContentType() {
        assertEquals("video/mp4", policy.validateUpload("VIDEO/MP4", 1_024));
        assertEquals("empty_media_file", assertThrows(BusinessException.class, () ->
                policy.validateUpload("video/mp4", 0)).code());
        assertEquals("media_file_too_large", assertThrows(BusinessException.class, () ->
                policy.validateUpload("video/mp4", 1_025)).code());
    }

    @Test
    void rejectsUnsupportedContentType() {
        BusinessException error = assertThrows(BusinessException.class, () ->
                policy.validateUpload("text/html", 100));

        assertEquals("unsupported_media_type", error.code());
    }
}
