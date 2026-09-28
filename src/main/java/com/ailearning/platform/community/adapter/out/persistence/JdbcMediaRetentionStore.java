package com.ailearning.platform.community.adapter.out.persistence;

import com.ailearning.platform.community.application.port.out.MediaRetentionStore;
import com.ailearning.platform.community.domain.model.ExpiredMedia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcMediaRetentionStore implements MediaRetentionStore {
    private final JdbcTemplate jdbc;

    public JdbcMediaRetentionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public List<ExpiredMedia> claimExpired(Instant now, int limit) {
        var rows =
                jdbc.query(
                        """
UPDATE community_media_assets SET cleanup_claimed_until=?
WHERE id IN (SELECT id FROM community_media_assets
 WHERE expires_at<=? AND storage_deleted_at IS NULL
 AND (cleanup_claimed_until IS NULL OR cleanup_claimed_until<=?)
 ORDER BY expires_at,id LIMIT ? FOR UPDATE SKIP LOCKED)
RETURNING id,object_key
""",
                        (rs, row) ->
                                new ExpiredMedia(
                                        rs.getObject("id", UUID.class), rs.getString("object_key")),
                        Timestamp.from(now.plus(Duration.ofMinutes(5))),
                        Timestamp.from(now),
                        Timestamp.from(now),
                        Math.min(100, Math.max(1, limit)));
        for (var media : rows)
            jdbc.update(
                    "UPDATE community_posts SET status='REMOVED',updated_at=? WHERE media_id=?",
                    Timestamp.from(now),
                    media.id());
        return rows;
    }

    @Override
    public void acknowledgeDeletion(UUID id, Instant now) {
        jdbc.update(
                "UPDATE community_media_assets SET storage_deleted_at=?,cleanup_claimed_until=NULL"
                    + " WHERE id=? AND expires_at<=?",
                Timestamp.from(now),
                id,
                Timestamp.from(now));
    }
}
