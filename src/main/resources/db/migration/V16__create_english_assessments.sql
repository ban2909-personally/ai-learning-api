CREATE TABLE practice_exams (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 1 AND 240),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE practice_sections (
    id UUID PRIMARY KEY,
    exam_id UUID NOT NULL REFERENCES practice_exams(id) ON DELETE CASCADE,
    skill VARCHAR(20) NOT NULL CHECK (skill IN ('LISTENING', 'READING', 'WRITING')),
    title VARCHAR(180) NOT NULL,
    passage TEXT,
    audio_text TEXT,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    UNIQUE (exam_id, display_order)
);

CREATE TABLE practice_questions (
    id UUID PRIMARY KEY,
    section_id UUID NOT NULL REFERENCES practice_sections(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CHOICE', 'TEXT', 'WRITING')),
    prompt TEXT NOT NULL,
    options TEXT[] NOT NULL DEFAULT '{}',
    correct_answer TEXT,
    explanation TEXT NOT NULL DEFAULT '',
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    UNIQUE (section_id, display_order),
    CONSTRAINT chk_practice_answer_key CHECK (
        (kind = 'WRITING' AND correct_answer IS NULL)
        OR (kind IN ('CHOICE', 'TEXT') AND correct_answer IS NOT NULL)
    )
);

CREATE TABLE practice_attempts (
    id UUID PRIMARY KEY,
    exam_id UUID NOT NULL REFERENCES practice_exams(id),
    owner_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('IN_PROGRESS', 'SUBMITTED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ
);
CREATE INDEX idx_practice_attempts_owner ON practice_attempts(owner_id, started_at DESC);

CREATE TABLE practice_answers (
    attempt_id UUID NOT NULL REFERENCES practice_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES practice_questions(id),
    answer TEXT NOT NULL,
    PRIMARY KEY (attempt_id, question_id)
);

INSERT INTO practice_exams (id, slug, title, description, duration_minutes, published)
VALUES ('31111111-1111-4111-8111-111111111111', 'english-workplace-starter',
        'English Workplace Starter',
        'Đề luyện tập tiếng Anh thực tế: nghe, đọc và viết. Điểm là số câu đúng, không phải điểm TOEIC chính thức.',
        35, TRUE);

INSERT INTO practice_sections (id, exam_id, skill, title, passage, audio_text, display_order)
VALUES
 ('32222222-2222-4222-8222-222222222221', '31111111-1111-4111-8111-111111111111',
  'LISTENING', 'Part 1 · Workplace announcement', NULL,
  'Attention, team. The weekly meeting has moved from Monday morning to Tuesday at nine thirty. Please bring your project updates to Room 204.', 0),
 ('32222222-2222-4222-8222-222222222222', '31111111-1111-4111-8111-111111111111',
  'READING', 'Part 2 · Read an email',
  'Subject: Training room booking. Hello Maya, your English workshop is confirmed for Thursday, 14 October, from 2 p.m. to 4 p.m. in Room B. Please send the participant list by Wednesday afternoon. Best, Daniel.',
  NULL, 1),
 ('32222222-2222-4222-8222-222222222223', '31111111-1111-4111-8111-111111111111',
  'WRITING', 'Part 3 · Reply to an email',
  'Your colleague has asked you to suggest a topic for the next English workshop.', NULL, 2);

INSERT INTO practice_questions (id, section_id, kind, prompt, options, correct_answer, explanation, display_order)
VALUES
 ('33333333-3333-4333-8333-333333333331', '32222222-2222-4222-8222-222222222221',
  'CHOICE', 'When is the weekly meeting?', ARRAY['Monday at 9:30', 'Tuesday at 9:30', 'Thursday at 2:00'],
  'Tuesday at 9:30', 'The speaker says the meeting moved to Tuesday at nine thirty.', 0),
 ('33333333-3333-4333-8333-333333333332', '32222222-2222-4222-8222-222222222221',
  'CHOICE', 'Where will the team meet?', ARRAY['Room 204', 'Room B', 'Online'],
  'Room 204', 'The announcement ends with “Room 204”.', 1),
 ('33333333-3333-4333-8333-333333333333', '32222222-2222-4222-8222-222222222222',
  'CHOICE', 'How long is the workshop?', ARRAY['One hour', 'Two hours', 'Three hours'],
  'Two hours', 'The email confirms 2 p.m. to 4 p.m.', 0),
 ('33333333-3333-4333-8333-333333333334', '32222222-2222-4222-8222-222222222222',
  'CHOICE', 'When should Maya send the participant list?', ARRAY['Wednesday afternoon', 'Thursday morning', 'Friday afternoon'],
  'Wednesday afternoon', 'Daniel requests the list by Wednesday afternoon.', 1),
 ('33333333-3333-4333-8333-333333333335', '32222222-2222-4222-8222-222222222223',
  'WRITING', 'Write a short email (about 80–120 words) proposing a useful workplace English topic and explaining why it would help your team.',
  ARRAY[]::TEXT[], NULL, '', 0);
