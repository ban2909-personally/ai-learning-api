package com.ailearning.platform.community.application.service.impl;

import com.ailearning.platform.community.api.contract.CommunityMediaRead;
import com.ailearning.platform.community.api.contract.CommunityMediaUpload;
import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.usecase.CommunityMediaUseCase;
import com.ailearning.platform.community.api.usecase.CommunityUseCase;
import com.ailearning.platform.community.application.port.out.CommunityMediaStorage;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.community.domain.model.MediaAsset;
import com.ailearning.platform.community.domain.model.MediaByteRange;
import com.ailearning.platform.community.domain.model.Membership;
import com.ailearning.platform.community.domain.model.Post;
import com.ailearning.platform.community.domain.model.PostStatus;
import com.ailearning.platform.community.domain.model.Space;
import com.ailearning.platform.community.domain.policy.CommunityMediaPolicy;
import com.ailearning.platform.community.domain.policy.CommunityPolicy;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.io.IOException;
import java.io.PushbackInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class CommunityMediaService implements CommunityMediaUseCase {
    private final CommunityStore store;
    private final CommunityMediaStorage storage;
    private final CommunityUseCase community;
    private final AccountAccess accounts;
    private final Clock clock;
    private final CommunityPolicy policy = new CommunityPolicy();
    private final CommunityMediaPolicy mediaPolicy = new CommunityMediaPolicy();

    public CommunityMediaService(
            CommunityStore store,
            CommunityMediaStorage storage,
            CommunityUseCase community,
            AccountAccess accounts,
            Clock clock) {
        this.store = store;
        this.storage = storage;
        this.community = community;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public PostView publish(UUID actor, UUID spaceId, String body, CommunityMediaUpload upload) {
        if (actor == null)
            throw new BusinessException(
                    "login_required", ErrorType.UNAUTHORIZED, "Hãy đăng nhập để đăng bài.");
        accounts.requireActive(actor);
        Space space = spaceId == null ? null : store.findSpace(spaceId).orElseThrow(this::missing);
        Membership member = spaceId == null ? null : store.membership(spaceId, actor).orElse(null);
        PostStatus status = policy.newPostStatus(space, member);
        String content = policy.postBody(body, true);
        if (upload == null || upload.content() == null)
            throw new BusinessException(
                    "invalid_media", ErrorType.BAD_REQUEST, "Thiếu file tải lên.");
        // Size is checked before reading even the signature or contacting storage.
        if (upload.sizeBytes() <= 0 || upload.sizeBytes() >= 10_000_000L) {
            mediaPolicy.validate(upload.contentType(), upload.sizeBytes(), new byte[0]);
        }
        UUID mediaId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        String key = "community/" + mediaId;
        try {
            PushbackInputStream input = new PushbackInputStream(upload.content(), 16);
            byte[] header = input.readNBytes(16);
            mediaPolicy.validate(upload.contentType(), upload.sizeBytes(), header);
            input.unread(header);
            String etag = storage.store(key, upload.contentType(), upload.sizeBytes(), input);
            MediaAsset asset =
                    new MediaAsset(
                            mediaId, actor, key, upload.contentType(), upload.sizeBytes(), etag);
            Post post =
                    new Post(
                            postId,
                            actor,
                            spaceId,
                            null,
                            content,
                            mediaId,
                            status,
                            Instant.now(clock));
            try {
                if (!store.createPost(post, asset))
                    throw new BusinessException(
                            "community_conflict",
                            ErrorType.CONFLICT,
                            "Quyền đăng bài đã thay đổi.");
            } catch (RuntimeException failure) {
                try {
                    storage.delete(key);
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        } catch (IOException failure) {
            throw new BusinessException(
                    "invalid_media", ErrorType.BAD_REQUEST, "Không đọc được file tải lên.");
        }
        // Persistence has committed: a response failure must not delete a referenced object.
        return community.post(actor, postId);
    }

    @Override
    public CommunityMediaRead read(UUID viewer, UUID postId, MediaByteRange range, boolean head) {
        community.post(viewer, postId); // Includes active-account, private and moderation checks.
        MediaAsset asset = store.postMedia(postId).orElseThrow(this::missing);
        long[] resolved =
                (range == null ? new MediaByteRange(null, null, null) : range)
                        .resolve(asset.sizeBytes());
        return new CommunityMediaRead(
                asset.contentType(),
                asset.etag(),
                asset.sizeBytes(),
                resolved[0],
                resolved[1],
                head ? null : storage.open(asset.objectKey(), resolved[0], resolved[1]));
    }

    private BusinessException missing() {
        return new BusinessException(
                "community_not_found", ErrorType.NOT_FOUND, "Không tìm thấy nội dung.");
    }
}
