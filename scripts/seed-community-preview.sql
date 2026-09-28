-- Optional, idempotent sample content for the isolated local preview database only.
-- Apply after Flyway V15 and seed-local-preview.sql; never add to production migrations.
BEGIN;
DO $$ BEGIN
    IF current_database() <> 'ai_learning_preview' THEN
        RAISE EXCEPTION 'Community preview seed is restricted to ai_learning_preview';
    END IF;
END $$;
SELECT pg_advisory_xact_lock(20260926);

INSERT INTO community_spaces(id,owner_id,kind,visibility,name,description,created_at,updated_at)
SELECT md5('community-preview-space-' || slug)::uuid, users.id, kind, visibility,
       name, description, CURRENT_TIMESTAMP - age, CURRENT_TIMESTAMP - age
FROM (VALUES
    ('java', 'lecture@demo.local', 'GROUP', 'PUBLIC', 'English Listening Circle',
     'Cùng luyện nghe hội thoại, thông báo và chia sẻ cách bắt từ khóa.', interval '3 days'),
    ('frontend', 'student@demo.local', 'GROUP', 'PUBLIC', 'Reading & Vocabulary Club',
     'Cùng đọc email tiếng Anh và ghi nhớ từ vựng theo ngữ cảnh.', interval '2 days'),
    ('mentor', 'leader@demo.local', 'GROUP', 'PRIVATE', 'English Writing Lab',
     'Nhóm riêng để góp ý cách viết email và diễn đạt ý tưởng bằng tiếng Anh.', interval '1 day'),
    ('academy', 'admin@demo.local', 'PAGE', 'PUBLIC', 'AI Learning · English Hub',
     'Thông báo, lộ trình và những câu chuyện học tiếng Anh từ cộng đồng.', interval '4 days')
) AS demo(slug,email,kind,visibility,name,description,age)
JOIN users ON users.email=demo.email
ON CONFLICT(id) DO NOTHING;

INSERT INTO community_members(space_id,user_id,role,status,joined_at)
SELECT s.id,s.owner_id,'OWNER','ACTIVE',s.created_at FROM community_spaces s
WHERE s.id IN (SELECT md5('community-preview-space-' || slug)::uuid
               FROM (VALUES ('java'),('frontend'),('mentor'),('academy')) x(slug))
ON CONFLICT DO NOTHING;

INSERT INTO community_members(space_id,user_id,role,status,joined_at)
SELECT md5('community-preview-space-' || space)::uuid,users.id,'MEMBER','ACTIVE',CURRENT_TIMESTAMP
FROM (VALUES ('java','student@demo.local'),('java','admin@demo.local'),
             ('frontend','lecture@demo.local'),('frontend','guest@demo.local'),
             ('academy','student@demo.local')) AS demo(space,email)
JOIN users ON users.email=demo.email
ON CONFLICT DO NOTHING;

INSERT INTO community_posts(id,author_id,space_id,body,created_at,updated_at)
SELECT md5('community-preview-post-' || n)::uuid,users.id,
       CASE WHEN space='' THEN NULL ELSE md5('community-preview-space-' || space)::uuid END,
       body,CURRENT_TIMESTAMP - age,CURRENT_TIMESTAMP - age
FROM (VALUES
    (1,'admin@demo.local','academy','Chào mừng đến với AI Learning! Đây là nơi cùng hỏi, cùng giải đáp và chia sẻ hành trình học tiếng Anh. Hôm nay bạn đang luyện kỹ năng nào?',interval '2 hours'),
    (2,'lecture@demo.local','java','Khi luyện nghe thông báo, hãy nghe ý chính trước rồi mới ghi thời gian, địa điểm và tên riêng. Bạn thường bỏ lỡ loại thông tin nào?',interval '95 minutes'),
    (3,'student@demo.local','','Mình vừa học cụm “Could you clarify that?” để hỏi lại lịch sự trong cuộc họp. Đặt câu trong tình huống thật giúp nhớ lâu hơn!',interval '65 minutes'),
    (4,'student@demo.local','frontend','Một mẹo luyện đọc email: xem dòng chủ đề, người gửi và yêu cầu hành động trước khi đọc từng câu. Mọi người có cách nào khác?',interval '38 minutes'),
    (5,'guest@demo.local','','Chào mọi người! Mình mới bắt đầu học tiếng Anh công việc. Nên luyện nghe và từ vựng theo lộ trình nào?',interval '20 minutes')
) AS demo(n,email,space,body,age)
JOIN users ON users.email=demo.email
ON CONFLICT(id) DO NOTHING;

INSERT INTO community_post_likes(post_id,user_id)
SELECT md5('community-preview-post-' || n)::uuid,users.id
FROM (VALUES (1,'student@demo.local'),(1,'lecture@demo.local'),
             (2,'admin@demo.local'),(2,'student@demo.local'),
             (3,'lecture@demo.local'),(4,'guest@demo.local')) AS demo(n,email)
JOIN users ON users.email=demo.email
ON CONFLICT DO NOTHING;

INSERT INTO community_comments(id,post_id,parent_id,author_id,body,created_at)
SELECT md5('community-preview-comment-' || n)::uuid,
       md5('community-preview-post-' || post)::uuid,
       CASE WHEN parent=0 THEN NULL ELSE md5('community-preview-comment-' || parent)::uuid END,
       users.id,body,CURRENT_TIMESTAMP - age
FROM (VALUES
    (1,5,0,'lecture@demo.local','Bắt đầu với hội thoại ngắn và 10 từ vựng theo chủ đề mỗi ngày. Sau đó thử đề luyện tập để biết mình hay sai ở đâu.',interval '13 minutes'),
    (2,5,1,'guest@demo.local','Cảm ơn thầy! Em sẽ thử theo lộ trình này.',interval '8 minutes'),
    (3,4,0,'admin@demo.local','Đúng rồi! Sau khi làm bài, hãy xem lại giải thích cho cả câu trả lời đúng lẫn câu sai.',interval '21 minutes')
) AS demo(n,post,parent,email,body,age)
JOIN users ON users.email=demo.email
ON CONFLICT(id) DO NOTHING;
COMMIT;
