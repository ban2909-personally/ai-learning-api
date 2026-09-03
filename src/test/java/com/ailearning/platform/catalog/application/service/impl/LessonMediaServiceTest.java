package com.ailearning.platform.catalog.application.service.impl;

import com.ailearning.platform.catalog.api.contract.LessonMediaUpload;
import com.ailearning.platform.catalog.application.port.out.LessonMediaCatalog;
import com.ailearning.platform.catalog.application.port.out.LessonMediaStorage;
import com.ailearning.platform.catalog.domain.model.LessonMediaAsset;
import com.ailearning.platform.catalog.domain.model.LessonMediaTarget;
import com.ailearning.platform.catalog.domain.policy.LessonMediaPolicy;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonMediaServiceTest {
    private final LessonMediaCatalog catalog = mock(LessonMediaCatalog.class);
    private final LessonMediaStorage storage = mock(LessonMediaStorage.class);
    private final LessonMediaService service = new LessonMediaService(
            catalog,
            storage,
            new LessonMediaPolicy(1_024, Set.of("video/mp4"))
    );

    @Test
    void storesMediaThenAttachesMetadata() {
        UUID ownerId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        var target = new LessonMediaTarget(courseId, "clean-java", lessonId, ownerId);
        var stored = new LessonMediaAsset("object-key", "video/mp4", 4, "etag-1");
        when(catalog.findForManagement("clean-java", lessonId)).thenReturn(Optional.of(target));
        when(storage.store(anyString(), anyString(), anyLong(), any())).thenReturn(stored);

        var result = service.upload(
                ownerId,
                false,
                "clean-java",
                lessonId,
                upload("video/mp4")
        );

        assertEquals("/api/v1/media/courses/clean-java/lessons/" + lessonId, result.contentUrl());
        verify(catalog).attach(lessonId, stored, result.contentUrl());
    }

    @Test
    void doesNotStoreWhenInstructorDoesNotOwnCourse() {
        UUID lessonId = UUID.randomUUID();
        when(catalog.findForManagement("clean-java", lessonId)).thenReturn(Optional.of(
                new LessonMediaTarget(UUID.randomUUID(), "clean-java", lessonId, UUID.randomUUID())
        ));

        assertThrows(RuntimeException.class, () -> service.upload(
                UUID.randomUUID(),
                false,
                "clean-java",
                lessonId,
                upload("video/mp4")
        ));

        verify(storage, never()).store(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void removesNewObjectWhenMetadataPersistenceFails() {
        UUID ownerId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        var target = new LessonMediaTarget(UUID.randomUUID(), "clean-java", lessonId, ownerId);
        var stored = new LessonMediaAsset("new-object", "video/mp4", 4, "etag-1");
        when(catalog.findForManagement("clean-java", lessonId)).thenReturn(Optional.of(target));
        when(storage.store(anyString(), anyString(), anyLong(), any())).thenReturn(stored);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(catalog).attach(any(), any(), anyString());

        assertThrows(IllegalStateException.class, () -> service.upload(
                ownerId,
                false,
                "clean-java",
                lessonId,
                upload("video/mp4")
        ));

        verify(storage).delete("new-object");
    }

    @Test
    void doesNotAttachMetadataWhenStorageFails() {
        UUID ownerId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        when(catalog.findForManagement("clean-java", lessonId)).thenReturn(Optional.of(
                new LessonMediaTarget(UUID.randomUUID(), "clean-java", lessonId, ownerId)
        ));
        when(storage.store(anyString(), anyString(), anyLong(), any())).thenThrow(
                new BusinessException(
                        "media_storage_unavailable",
                        ErrorType.SERVICE_UNAVAILABLE,
                        "Storage unavailable"
                )
        );

        BusinessException error = assertThrows(BusinessException.class, () -> service.upload(
                ownerId,
                false,
                "clean-java",
                lessonId,
                upload("video/mp4")
        ));

        assertEquals("media_storage_unavailable", error.code());
        verify(catalog, never()).attach(any(), any(), anyString());
        verify(storage, never()).delete(anyString());
    }

    private LessonMediaUpload upload(String contentType) {
        byte[] content = {1, 2, 3, 4};
        return new LessonMediaUpload(contentType, content.length, new ByteArrayInputStream(content));
    }
}
