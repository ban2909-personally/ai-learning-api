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
    ('java', 'lecture@demo.local', 'GROUP', 'PUBLIC', 'Cùng học Java & Spring',
     'Hỏi đáp về Java, Spring Boot và cách thiết kế backend dễ bảo trì.', interval '3 days'),
    ('frontend', 'student@demo.local', 'GROUP', 'PUBLIC', 'Frontend thực chiến',
     'Chia sẻ kinh nghiệm React, TypeScript và thiết kế giao diện thân thiện.', interval '2 days'),
    ('mentor', 'leader@demo.local', 'GROUP', 'PRIVATE', 'Câu lạc bộ mentor',
     'Nhóm riêng dành cho những người cùng hướng dẫn và phản biện bài học.', interval '1 day'),
    ('academy', 'admin@demo.local', 'PAGE', 'PUBLIC', 'AI Learning · Góc chia sẻ',
     'Thông báo, lộ trình và những câu chuyện học tập từ cộng đồng.', interval '4 days')
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
    (1,'admin@demo.local','academy','Chào mừng đến với AI Learning! Bảng tin này là nơi mọi người cùng hỏi, cùng giải đáp và chia sẻ điều vừa học được. Hãy bắt đầu bằng một câu hỏi nhỏ hôm nay.',interval '2 hours'),
    (2,'lecture@demo.local','java','Trong Spring Boot, hãy bắt đầu từ một use case rõ ràng rồi mới chọn adapter. Khi thay đổi database, logic nghiệp vụ vẫn nên đứng vững. Bạn đang gặp khó ở phần nào?',interval '95 minutes'),
    (3,'student@demo.local','','Mình vừa học về HTTP 401 và 403: 401 là chưa được xác thực; 403 là đã xác định người gọi nhưng không có quyền. Viết lại bằng lời của mình giúp nhớ lâu hơn!',interval '65 minutes'),
    (4,'student@demo.local','frontend','Một mẹo nhỏ khi học React: thử viết component nhỏ có trạng thái rõ ràng, test một hành vi trước khi thêm thật nhiều tính năng. Mọi người có workflow nào hay?',interval '38 minutes'),
    (5,'guest@demo.local','','Chào mọi người! Mình mới tham gia và muốn tìm lộ trình học Java từ đầu. Nên bắt đầu từ những kiến thức nào?',interval '20 minutes')
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
    (1,5,0,'lecture@demo.local','Bắt đầu với biến, hàm, kiểu dữ liệu và lập trình hướng đối tượng. Sau đó thử một dự án nhỏ trước khi học Spring.',interval '13 minutes'),
    (2,5,1,'guest@demo.local','Cảm ơn thầy! Em sẽ thử theo lộ trình này.',interval '8 minutes'),
    (3,4,0,'admin@demo.local','Viết test cho tương tác chính từ sớm sẽ giúp refactor tự tin hơn.',interval '21 minutes')
) AS demo(n,post,parent,email,body,age)
JOIN users ON users.email=demo.email
ON CONFLICT(id) DO NOTHING;
COMMIT;
