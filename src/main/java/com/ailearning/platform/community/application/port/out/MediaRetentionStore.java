package com.ailearning.platform.community.application.port.out;

import com.ailearning.platform.community.domain.model.ExpiredMedia;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaRetentionStore {
    List<ExpiredMedia> claimExpired(Instant now, int limit);

    void acknowledgeDeletion(UUID id, Instant now);
}
