package com.ailearning.platform.community.adapter.in.scheduling;

import com.ailearning.platform.community.api.usecase.MediaRetentionUseCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.community.retention.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MediaRetentionJob {
    private static final Logger LOG = LoggerFactory.getLogger(MediaRetentionJob.class);
    private final MediaRetentionUseCase retention;

    public MediaRetentionJob(MediaRetentionUseCase retention) {
        this.retention = retention;
    }

    @Scheduled(
            fixedDelayString = "${app.community.retention.delay:PT1M}",
            initialDelayString = "${app.community.retention.delay:PT1M}")
    public void cleanup() {
        var result = retention.cleanup();
        if (result.failed() > 0)
            LOG.warn(
                    "Community media cleanup: {} claimed, {} deleted, {} awaiting retry",
                    result.claimed(),
                    result.deleted(),
                    result.failed());
    }
}
