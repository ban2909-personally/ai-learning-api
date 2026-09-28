CREATE TABLE practice_exam_series (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE,
    author_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO practice_exam_series(id,slug,created_at)
SELECT id,slug,created_at FROM practice_exams;

ALTER TABLE practice_exams
    ADD COLUMN series_id UUID REFERENCES practice_exam_series(id) ON DELETE CASCADE,
    ADD COLUMN revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PENDING_REVIEW','PUBLISHED','ARCHIVED')),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN published_by UUID REFERENCES users(id);
UPDATE practice_exams SET series_id=id,
    status=CASE WHEN published THEN 'PUBLISHED' ELSE 'DRAFT' END,
    published_at=CASE WHEN published THEN created_at END;
ALTER TABLE practice_exams ALTER COLUMN series_id SET NOT NULL;
ALTER TABLE practice_exams DROP COLUMN slug, DROP COLUMN published;
CREATE UNIQUE INDEX uq_practice_exam_revision ON practice_exams(series_id,revision);
CREATE UNIQUE INDEX uq_practice_exam_publication ON practice_exams(series_id)
    WHERE status='PUBLISHED';
CREATE UNIQUE INDEX uq_practice_exam_editable ON practice_exams(series_id)
    WHERE status IN ('DRAFT','PENDING_REVIEW');
CREATE INDEX idx_practice_exam_author ON practice_exam_series(author_id,created_at DESC);
