package com.ailearning.platform.community.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailearning.platform.community.api.contract.CommunityMediaUpload;
import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.usecase.CommunityUseCase;
import com.ailearning.platform.community.application.port.out.CommunityMediaStorage;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.community.domain.model.*;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

class CommunityMediaServiceTest {
    CommunityStore store = mock(CommunityStore.class);
    CommunityMediaStorage storage = mock(CommunityMediaStorage.class);
    CommunityUseCase community = mock(CommunityUseCase.class);
    AccountAccess accounts = mock(AccountAccess.class);
    CommunityMediaService service;
    UUID actor = UUID.randomUUID();
    byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0, 0, 0, 0, 0};

    @BeforeEach
    void setup() {
        service =
                new CommunityMediaService(
                        store,
                        storage,
                        community,
                        accounts,
                        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private CommunityMediaUpload upload() {
        return new CommunityMediaUpload("image/png", png.length, new ByteArrayInputStream(png));
    }

    @Test
    void publishesMediaOnlyPostWithOpaqueOwnedAsset() {
        when(storage.store(anyString(), eq("image/png"), eq((long) png.length), any()))
                .thenReturn("etag");
        when(store.createPost(any(), any(), any())).thenReturn(true);
        PostView view = mock(PostView.class);
        when(community.post(eq(actor), any())).thenReturn(view);
        assertSame(view, service.publish(actor, null, "", upload(), null));
        ArgumentCaptor<Post> post = ArgumentCaptor.forClass(Post.class);
        ArgumentCaptor<MediaAsset> asset = ArgumentCaptor.forClass(MediaAsset.class);
        verify(store).createPost(post.capture(), asset.capture(), isNull());
        assertEquals(PostStatus.ACTIVE, post.getValue().status());
        assertEquals(actor, asset.getValue().ownerId());
        assertEquals(post.getValue().mediaId(), asset.getValue().id());
        assertTrue(asset.getValue().objectKey().startsWith("community/"));
        verify(storage, never()).delete(anyString());
    }

    @Test
    void enforcesMembershipBeforeReadingOrUploadingFile() {
        UUID spaceId = UUID.randomUUID();
        when(store.findSpace(spaceId))
                .thenReturn(
                        Optional.of(
                                new Space(
                                        spaceId,
                                        UUID.randomUUID(),
                                        SpaceKind.GROUP,
                                        SpaceVisibility.PRIVATE,
                                        "Club",
                                        "",
                                        Instant.EPOCH)));
        assertThrows(
                BusinessException.class, () -> service.publish(actor, spaceId, "", upload(), null));
        verifyNoInteractions(storage);
    }

    @Test
    void cleansStorageIfPersistenceRejectsChangedPermissionsButNotOnResponseFailure() {
        when(storage.store(anyString(), anyString(), anyLong(), any())).thenReturn("etag");
        when(store.createPost(any(), any(), any())).thenReturn(false);
        assertThrows(
                BusinessException.class, () -> service.publish(actor, null, "", upload(), null));
        verify(storage).delete(anyString());
        clearInvocations(storage);
        when(store.createPost(any(), any(), any())).thenReturn(true);
        when(community.post(eq(actor), any()))
                .thenThrow(new IllegalStateException("response unavailable"));
        assertThrows(
                IllegalStateException.class,
                () -> service.publish(actor, null, "", upload(), null));
        verify(storage, never()).delete(anyString());
    }

    @Test
    void rejectsOversizedOrDisguisedFilesWithoutStorage() {
        assertThrows(
                BusinessException.class,
                () ->
                        service.publish(
                                actor,
                                null,
                                "",
                                new CommunityMediaUpload(
                                        "image/png", 10_000_000, new ByteArrayInputStream(png)),
                                null));
        assertThrows(
                BusinessException.class,
                () ->
                        service.publish(
                                actor,
                                null,
                                "",
                                new CommunityMediaUpload(
                                        "text/html", 16, new ByteArrayInputStream(png)),
                                null));
        assertThrows(
                BusinessException.class, () -> service.publish(null, null, "", upload(), null));
        verifyNoInteractions(storage);
    }

    @Test
    void deniedMediaReadNeverOpensStorageAndHeadOnlyReadsMetadata() throws Exception {
        UUID postId = UUID.randomUUID();
        when(community.post(null, postId))
                .thenThrow(new BusinessException("private_space", ErrorType.FORBIDDEN, "Private"));
        assertThrows(BusinessException.class, () -> service.read(null, postId, null, false));
        verifyNoInteractions(storage);
        when(store.postMedia(postId))
                .thenReturn(
                        Optional.of(
                                new MediaAsset(
                                        UUID.randomUUID(),
                                        actor,
                                        "community/test",
                                        "image/png",
                                        16,
                                        "etag")));
        try (var head = service.read(actor, postId, null, true)) {
            assertNull(head.content());
            assertEquals(16, head.length());
        }
        verifyNoInteractions(storage);
        when(storage.open("community/test", 4, 4))
                .thenReturn(new ByteArrayInputStream(new byte[4]));
        try (var range = service.read(actor, postId, new MediaByteRange(4L, 7L, null), false)) {
            assertEquals(4, range.start());
            assertEquals(4, range.length());
            assertEquals(4, range.content().readAllBytes().length);
        }
    }
}
