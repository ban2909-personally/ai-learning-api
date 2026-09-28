package com.ailearning.platform.community.application.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailearning.platform.community.application.port.out.*;
import com.ailearning.platform.community.domain.model.ExpiredMedia;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

class MediaRetentionServiceTest {
    @Test
    void failedDeleteIsNotAcknowledgedAndOtherItemsContinue() {
        var store = mock(MediaRetentionStore.class);
        var storage = mock(CommunityMediaStorage.class);
        var now = Instant.parse("2026-09-15T00:00:00Z");
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(store.claimExpired(now, 100))
                .thenReturn(
                        List.of(
                                new ExpiredMedia(first, "community/" + first),
                                new ExpiredMedia(second, "community/" + second)));
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage)
                .delete("community/" + first);
        var result =
                new MediaRetentionService(store, storage, Clock.fixed(now, ZoneOffset.UTC))
                        .cleanup();
        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(store, never()).acknowledgeDeletion(first, now);
        verify(store).acknowledgeDeletion(second, now);
    }

    @Test
    void refusesUnrelatedBucketObjectsBeforeDeletion() {
        var store = mock(MediaRetentionStore.class);
        var storage = mock(CommunityMediaStorage.class);
        var now = Instant.now();
        when(store.claimExpired(now, 100))
                .thenReturn(List.of(new ExpiredMedia(UUID.randomUUID(), "lesson-media/other")));
        assertThat(
                        new MediaRetentionService(store, storage, Clock.fixed(now, ZoneOffset.UTC))
                                .cleanup()
                                .failed())
                .isEqualTo(1);
        verifyNoInteractions(storage);
        verify(store, never()).acknowledgeDeletion(any(), any());
    }
}
