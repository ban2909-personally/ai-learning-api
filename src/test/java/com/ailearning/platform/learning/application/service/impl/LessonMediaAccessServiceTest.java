package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.catalog.api.contract.LessonMediaStream;
import com.ailearning.platform.catalog.api.contract.MediaRangeRequest;
import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningMediaLookup;
import com.ailearning.platform.learning.api.usecase.LessonAccessUseCase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LessonMediaAccessServiceTest {
    @Test
    void authorizesLessonBeforeOpeningStorage() {
        LessonAccessUseCase lessonAccess = mock(LessonAccessUseCase.class);
        CourseLearningMediaLookup media = mock(CourseLearningMediaLookup.class);
        var service = new LessonMediaAccessService(lessonAccess, media);
        UUID userId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        MediaRangeRequest range = MediaRangeRequest.from(100);
        var expected = new LessonMediaStream(
                new ByteArrayInputStream(new byte[10]),
                "video/mp4",
                100,
                10,
                110,
                "etag-1"
        );
        when(media.openPublishedLessonMedia("clean-java", lessonId, range)).thenReturn(expected);

        assertSame(expected, service.open(userId, "clean-java", lessonId, range));

        var ordered = inOrder(lessonAccess, media);
        ordered.verify(lessonAccess).openLesson(userId, "clean-java", lessonId);
        ordered.verify(media).openPublishedLessonMedia("clean-java", lessonId, range);
    }
}
