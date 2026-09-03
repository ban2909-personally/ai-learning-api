package com.ailearning.platform.catalog.application.service.impl;

import com.ailearning.platform.catalog.api.contract.MediaRangeRequest;
import com.ailearning.platform.catalog.application.port.out.LessonMediaCatalog;
import com.ailearning.platform.catalog.application.port.out.LessonMediaStorage;
import com.ailearning.platform.catalog.domain.model.LessonMediaAsset;
import com.ailearning.platform.catalog.domain.model.PublishedLessonMedia;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonMediaDeliveryServiceTest {
    @Test
    void opensOnlyResolvedObjectRange() throws Exception {
        LessonMediaCatalog catalog = mock(LessonMediaCatalog.class);
        LessonMediaStorage storage = mock(LessonMediaStorage.class);
        var service = new LessonMediaDeliveryService(catalog, storage);
        UUID lessonId = UUID.randomUUID();
        var asset = new LessonMediaAsset("object-key", "video/mp4", 1_000, "etag-1");
        when(catalog.findPublished("clean-java", lessonId)).thenReturn(Optional.of(
                new PublishedLessonMedia(UUID.randomUUID(), "clean-java", lessonId, asset)
        ));
        when(storage.open("object-key", 900, 100)).thenReturn(new ByteArrayInputStream(new byte[100]));

        try (var stream = service.openPublishedLessonMedia(
                "clean-java",
                lessonId,
                MediaRangeRequest.from(900)
        )) {
            assertEquals(900, stream.start());
            assertEquals(100, stream.length());
            assertEquals(1_000, stream.totalLength());
        }
        verify(storage).open("object-key", 900, 100);
    }
}
