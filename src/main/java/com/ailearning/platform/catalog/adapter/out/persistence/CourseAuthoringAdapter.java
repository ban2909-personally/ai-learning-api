package com.ailearning.platform.catalog.adapter.out.persistence;

import com.ailearning.platform.catalog.api.contract.CreateCourseCommand;
import com.ailearning.platform.catalog.api.contract.CreateLessonCommand;
import com.ailearning.platform.catalog.application.port.out.CourseAuthoringStore;
import com.ailearning.platform.catalog.domain.model.ManagedCourse;
import com.ailearning.platform.catalog.domain.model.ManagedLesson;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CourseAuthoringAdapter implements CourseAuthoringStore {
    private static final String SELECT =
            """
            SELECT c.*, (SELECT count(*) FROM lessons l JOIN course_sections s ON s.id=l.section_id
             WHERE s.course_id=c.id) AS lesson_count FROM courses c
            """;
    private static final RowMapper<ManagedCourse> MAPPER =
            (rs, row) ->
                    new ManagedCourse(
                            rs.getObject("id", UUID.class),
                            rs.getObject("instructor_id", UUID.class),
                            rs.getObject("category_id", UUID.class),
                            rs.getString("slug"),
                            rs.getString("title"),
                            rs.getString("short_description"),
                            rs.getString("description"),
                            rs.getString("level"),
                            rs.getBigDecimal("price"),
                            rs.getString("status"),
                            rs.getInt("lesson_count"),
                            rs.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;

    public CourseAuthoringAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public java.util.Map<String, Long> statistics(UUID owner) {
        var result = new java.util.LinkedHashMap<String, Long>();
        org.springframework.jdbc.core.RowCallbackHandler collect =
                rs -> result.put(rs.getString("status"), rs.getLong("total"));
        if (owner == null)
            jdbc.query("SELECT status,count(*) AS total FROM courses GROUP BY status", collect);
        else
            jdbc.query(
                    "SELECT status,count(*) AS total FROM courses WHERE instructor_id=? GROUP BY"
                            + " status",
                    collect,
                    owner);
        result.put("total", result.values().stream().mapToLong(Long::longValue).sum());
        return result;
    }

    @Override
    public List<ManagedCourse> list(UUID owner, int page) {
        if (owner == null)
            return jdbc.query(
                    SELECT + " ORDER BY c.updated_at DESC,c.id LIMIT 50 OFFSET ?",
                    MAPPER,
                    (long) page * 50);
        return jdbc.query(
                SELECT
                        + " WHERE c.instructor_id=? ORDER BY c.updated_at DESC,c.id LIMIT 50 OFFSET"
                        + " ?",
                MAPPER,
                owner,
                (long) page * 50);
    }

    @Override
    public Optional<ManagedCourse> find(UUID course, boolean lock) {
        return jdbc
                .query(SELECT + " WHERE c.id=?" + (lock ? " FOR UPDATE OF c" : ""), MAPPER, course)
                .stream()
                .findFirst();
    }

    @Override
    public List<ManagedLesson> lessons(UUID course) {
        return jdbc.query(
                """
SELECT l.*,s.title AS section_title FROM lessons l JOIN course_sections s ON s.id=l.section_id
WHERE s.course_id=? ORDER BY s.display_order,l.display_order
""",
                (rs, row) ->
                        new ManagedLesson(
                                rs.getObject("id", UUID.class),
                                rs.getString("section_title"),
                                rs.getString("title"),
                                rs.getString("content_url"),
                                rs.getInt("duration_seconds"),
                                rs.getBoolean("preview")),
                course);
    }

    @Override
    public ManagedCourse create(UUID owner, CreateCourseCommand command) {
        if (jdbc.queryForObject(
                        "SELECT count(*) FROM categories WHERE id=?",
                        Long.class,
                        command.categoryId())
                == 0) {
            throw new BusinessException(
                    "category_not_found", ErrorType.NOT_FOUND, "Không tìm thấy danh mục.");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    """
INSERT INTO courses(id,instructor_id,category_id,slug,title,short_description,description,level,
  language,price,currency,status) VALUES (?,?,?,?,?,?,?,?,'vi',?,'VND','DRAFT')
""",
                    id,
                    owner,
                    command.categoryId(),
                    command.slug(),
                    command.title().trim(),
                    command.shortDescription().trim(),
                    command.description().trim(),
                    command.level(),
                    command.price());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "course_slug_exists",
                    ErrorType.CONFLICT,
                    "Đường dẫn khóa học đã được sử dụng.");
        }
        return find(id, false).orElseThrow();
    }

    @Override
    public void addLesson(UUID course, CreateLessonCommand command) {
        var sections =
                jdbc.queryForList(
                        "SELECT id FROM course_sections WHERE course_id=? AND title=? ORDER BY"
                                + " display_order LIMIT 1",
                        UUID.class,
                        course,
                        command.sectionTitle().trim());
        UUID section = sections.isEmpty() ? UUID.randomUUID() : sections.getFirst();
        if (sections.isEmpty()) {
            jdbc.update(
                    """
INSERT INTO course_sections(id,course_id,title,display_order)
SELECT ?,?,?,COALESCE(max(display_order),-1)+1 FROM course_sections WHERE course_id=?
""",
                    section,
                    course,
                    command.sectionTitle().trim(),
                    course);
        }
        jdbc.update(
                """
INSERT INTO lessons(id,section_id,title,content_url,duration_seconds,preview,display_order)
SELECT ?,?,?,?,?,?,COALESCE(max(display_order),-1)+1 FROM lessons WHERE section_id=?
""",
                UUID.randomUUID(),
                section,
                command.title().trim(),
                command.contentUrl().trim(),
                command.durationSeconds(),
                command.preview(),
                section);
        jdbc.update(
                """
UPDATE courses SET updated_at=CURRENT_TIMESTAMP,estimated_duration_minutes=
 (SELECT COALESCE(sum(l.duration_seconds),0)/60 FROM lessons l JOIN course_sections s ON s.id=l.section_id WHERE s.course_id=?)
WHERE id=?
""",
                course,
                course);
    }

    @Override
    public void transition(UUID course, String status) {
        jdbc.update(
                """
UPDATE courses SET status=?,updated_at=CURRENT_TIMESTAMP,
published_at=CASE WHEN ?='PUBLISHED' THEN CURRENT_TIMESTAMP ELSE published_at END WHERE id=?
""",
                status,
                status,
                course);
    }
}
