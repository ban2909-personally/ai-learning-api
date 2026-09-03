ALTER TABLE lessons
    ADD COLUMN media_object_key VARCHAR(700),
    ADD COLUMN media_content_type VARCHAR(100),
    ADD COLUMN media_size_bytes BIGINT,
    ADD COLUMN media_etag VARCHAR(128);

ALTER TABLE lessons
    ADD CONSTRAINT chk_lesson_media_metadata_complete CHECK (
        (media_object_key IS NULL
            AND media_content_type IS NULL
            AND media_size_bytes IS NULL
            AND media_etag IS NULL)
        OR
        (media_object_key IS NOT NULL
            AND media_content_type IS NOT NULL
            AND media_size_bytes > 0
            AND media_etag IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_lessons_media_object_key
    ON lessons(media_object_key)
    WHERE media_object_key IS NOT NULL;
