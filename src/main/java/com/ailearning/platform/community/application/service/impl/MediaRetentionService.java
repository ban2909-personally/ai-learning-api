package com.ailearning.platform.community.application.service.impl;

import com.ailearning.platform.community.api.contract.RetentionResult;
import com.ailearning.platform.community.api.usecase.MediaRetentionUseCase;
import com.ailearning.platform.community.application.port.out.CommunityMediaStorage;
import com.ailearning.platform.community.application.port.out.MediaRetentionStore;
import com.ailearning.platform.community.domain.policy.MediaRetentionPolicy;

import java.time.Clock;
import java.time.Instant;

public class MediaRetentionService implements MediaRetentionUseCase {
    private final MediaRetentionStore store;
    private final CommunityMediaStorage storage;
    private final Clock clock;
    private final MediaRetentionPolicy policy = new MediaRetentionPolicy();

    public MediaRetentionService(
            MediaRetentionStore store, CommunityMediaStorage storage, Clock clock) {
        this.store = store;
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    public RetentionResult cleanup() {
        Instant now = Instant.now(clock);
        var claimed = store.claimExpired(now, 100);
        int deleted = 0;
        for (var media : claimed) {
            try {
                policy.requireOwnedObject(media.id(), media.objectKey());
                storage.delete(media.objectKey());
                store.acknowledgeDeletion(media.id(), now);
                deleted++;
            } catch (RuntimeException failure) {
                // The durable lease expires for retry; expired posts remain inaccessible.
                System.getLogger(MediaRetentionService.class.getName())
                        .log(
                                System.Logger.Level.WARNING,
                                "Community media cleanup awaiting retry for asset " + media.id(),
                                failure);
            }
        }
        return new RetentionResult(claimed.size(), deleted, claimed.size() - deleted);
    }
}
