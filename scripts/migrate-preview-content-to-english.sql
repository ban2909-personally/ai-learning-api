-- One-time, non-destructive correction for the isolated local preview database.
-- Only rows with the original generated IDs AND original text are updated.
-- This does not reset accounts, enrollments, attempts, comments or user edits.
BEGIN;
DO $$ BEGIN
    IF current_database() <> 'ai_learning_preview' THEN
        RAISE EXCEPTION 'English preview correction is restricted to ai_learning_preview';
    END IF;
END $$;
SELECT pg_advisory_xact_lock(20260927);

INSERT INTO categories(id,slug,name,description,display_order)
SELECT md5('preview-english-category-' || slug)::uuid,slug,name,description,display_order
FROM (VALUES
 ('listening','Nghe tiếng Anh','Luyện nghe hội thoại và thông báo thực tế.',10),
 ('reading','Đọc tiếng Anh','Đọc hiểu email, thông báo và tài liệu.',20),
 ('writing','Viết tiếng Anh','Viết email và diễn đạt ý tưởng rõ ràng.',30),
 ('exam-prep','Luyện thi','Chiến lược và đề thi thử tiếng Anh.',40)
) AS demo(slug,name,description,display_order)
ON CONFLICT(slug) DO NOTHING;

UPDATE courses AS course
SET category_id=category.id, slug=demo.new_slug, title=demo.new_title,
    short_description=demo.summary,
    description=demo.summary || E'\n\nKhóa học minh họa cho bản local; nội dung đầy đủ đang được biên soạn.',
    status='PUBLISHED', published_at=COALESCE(course.published_at,CURRENT_TIMESTAMP),
    updated_at=CURRENT_TIMESTAMP
FROM (VALUES
 (1,'Java từ con số không','listening','luyen-nghe-tieng-anh-can-ban','Luyện nghe tiếng Anh căn bản','Làm quen với hội thoại ngắn, nhận biết thông tin chính và từ khóa.'),
 (2,'React & TypeScript thực chiến','reading','doc-hieu-email-cong-viec','Đọc hiểu email công việc','Rèn kỹ năng tìm ý chính và chi tiết trong email tiếng Anh.'),
 (3,'Xây dựng API với Spring Boot','listening','luyen-nghe-theo-dang-toeic','Luyện nghe theo dạng TOEIC','Thực hành nghe mô tả, hội thoại và thông báo trong môi trường công việc.'),
 (4,'Ứng dụng AI trong lập trình','reading','luyen-doc-theo-dang-toeic','Luyện đọc theo dạng TOEIC','Ôn ngữ pháp, từ vựng và đọc hiểu văn bản công việc.'),
 (5,'Docker cho lập trình viên','writing','viet-email-tieng-anh-cong-viec','Viết email tiếng Anh công việc','Viết email rõ mục đích, đúng ngữ cảnh và giọng điệu.'),
 (6,'Clean Architecture trong thực tế','exam-prep','chien-luoc-lam-de-tieng-anh','Chiến lược làm đề tiếng Anh','Quản lý thời gian, phân tích lỗi sai và luyện thi thử hiệu quả.')
) AS demo(n,old_title,category_slug,new_slug,new_title,summary)
JOIN categories AS category ON category.slug=demo.category_slug
WHERE course.id=md5('preview-course-' || demo.n)::uuid
  AND course.title=demo.old_title;

UPDATE flashcard_decks AS deck
SET title='English · Workplace vocabulary',
    description='Ôn tập từ vựng và mẫu câu tiếng Anh.'
WHERE deck.title='Lập trình · Kiến thức cốt lõi'
  AND deck.id IN (SELECT md5('preview-deck-' || email)::uuid
                  FROM users WHERE email LIKE '%@demo.local');

UPDATE flashcards AS card SET front=demo.new_front,back=demo.new_back
FROM (VALUES
 (1,'JVM là gì?','schedule','Lịch trình; ví dụ: The meeting is on my schedule.'),
 (2,'HTTP 401 khác HTTP 403 thế nào?','Could you clarify that?','Bạn có thể làm rõ điều đó không? Dùng khi cần hỏi lại trong cuộc họp.'),
 (3,'Dependency Inversion là gì?','deadline','Hạn chót; ví dụ: The deadline is Friday afternoon.')
) AS demo(n,old_front,new_front,new_back)
WHERE card.id=md5(card.deck_id::text || '-' || demo.n)::uuid
  AND card.front=demo.old_front;

UPDATE community_spaces AS space SET name=demo.new_name,description=demo.new_description
FROM (VALUES
 ('java','Cùng học Java & Spring','English Listening Circle','Cùng luyện nghe hội thoại, thông báo và chia sẻ cách bắt từ khóa.'),
 ('frontend','Frontend thực chiến','Reading & Vocabulary Club','Cùng đọc email tiếng Anh và ghi nhớ từ vựng theo ngữ cảnh.'),
 ('mentor','Câu lạc bộ mentor','English Writing Lab','Nhóm riêng để góp ý cách viết email và diễn đạt ý tưởng bằng tiếng Anh.'),
 ('academy','AI Learning · Góc chia sẻ','AI Learning · English Hub','Thông báo, lộ trình và những câu chuyện học tiếng Anh từ cộng đồng.')
) AS demo(slug,old_name,new_name,new_description)
WHERE space.id=md5('community-preview-space-' || demo.slug)::uuid
  AND space.name=demo.old_name;

UPDATE community_posts AS post SET body=demo.new_body,updated_at=CURRENT_TIMESTAMP
FROM (VALUES
 (1,'Chào mừng đến với AI Learning! Bảng tin này%',
  'Chào mừng đến với AI Learning! Đây là nơi cùng hỏi, cùng giải đáp và chia sẻ hành trình học tiếng Anh. Hôm nay bạn đang luyện kỹ năng nào?'),
 (2,'Trong Spring Boot%',
  'Khi luyện nghe thông báo, hãy nghe ý chính trước rồi mới ghi thời gian, địa điểm và tên riêng. Bạn thường bỏ lỡ loại thông tin nào?'),
 (3,'Mình vừa học về HTTP 401%',
  'Mình vừa học cụm “Could you clarify that?” để hỏi lại lịch sự trong cuộc họp. Đặt câu trong tình huống thật giúp nhớ lâu hơn!'),
 (4,'Một mẹo nhỏ khi học React%',
  'Một mẹo luyện đọc email: xem dòng chủ đề, người gửi và yêu cầu hành động trước khi đọc từng câu. Mọi người có cách nào khác?'),
 (5,'Chào mọi người! Mình mới tham gia và muốn tìm lộ trình học Java%',
  'Chào mọi người! Mình mới bắt đầu học tiếng Anh công việc. Nên luyện nghe và từ vựng theo lộ trình nào?')
) AS demo(n,old_pattern,new_body)
WHERE post.id=md5('community-preview-post-' || demo.n)::uuid
  AND post.body LIKE demo.old_pattern;

UPDATE community_comments AS comment SET body=demo.new_body
FROM (VALUES
 (1,'Bắt đầu với biến, hàm%',
  'Bắt đầu với hội thoại ngắn và 10 từ vựng theo chủ đề mỗi ngày. Sau đó thử đề luyện tập để biết mình hay sai ở đâu.'),
 (3,'Viết test cho tương tác chính%',
  'Đúng rồi! Sau khi làm bài, hãy xem lại giải thích cho cả câu trả lời đúng lẫn câu sai.')
) AS demo(n,old_pattern,new_body)
WHERE comment.id=md5('community-preview-comment-' || demo.n)::uuid
  AND comment.body LIKE demo.old_pattern;
COMMIT;
