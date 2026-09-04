package com.ailearning.platform.analytics.adapter.out.persistence;

import com.ailearning.platform.analytics.application.port.out.CompletionAnalyticsSnapshot;
import com.ailearning.platform.analytics.application.port.out.CompletionAnalyticsStore;
import com.ailearning.platform.analytics.application.port.out.CourseCompletionAggregate;
import com.ailearning.platform.analytics.domain.model.CompletionFact;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class CompletionAnalyticsPersistenceAdapter implements CompletionAnalyticsStore {
    private static final String INSERT_SQL = """
            INSERT INTO learning_completion_facts (
                event_id, user_id, enrollment_id, course_id, lesson_id, completed_at, projected_at
            ) VALUES (
                :eventId, :userId, :enrollmentId, :courseId, :lessonId, :completedAt, :projectedAt
            )
            ON CONFLICT DO NOTHING
            """;

    private static final String SUMMARY_SQL = """
            WITH course_completions AS (
                SELECT course_id,
                       COUNT(*) AS completed_lessons,
                       MAX(completed_at) AS last_completed_at
                FROM learning_completion_facts
                WHERE user_id = :userId
                GROUP BY course_id
            ), summarized AS (
                SELECT course_id,
                       completed_lessons,
                       last_completed_at,
                       SUM(completed_lessons) OVER () AS total_completed_lessons,
                       COUNT(*) OVER () AS total_courses,
                       MAX(last_completed_at) OVER () AS overall_last_completed_at
                FROM course_completions
            )
            SELECT course_id,
                   completed_lessons,
                   last_completed_at,
                   total_completed_lessons,
                   total_courses,
                   overall_last_completed_at
            FROM summarized
            ORDER BY last_completed_at DESC, course_id
            LIMIT :courseLimit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CompletionAnalyticsPersistenceAdapter(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public boolean append(CompletionFact fact) {
        var parameters = new MapSqlParameterSource()
                .addValue("eventId", fact.eventId())
                .addValue("userId", fact.userId())
                .addValue("enrollmentId", fact.enrollmentId())
                .addValue("courseId", fact.courseId())
                .addValue("lessonId", fact.lessonId())
                .addValue("completedAt", Timestamp.from(fact.completedAt()))
                .addValue("projectedAt", Timestamp.from(fact.projectedAt()));
        return jdbc.update(INSERT_SQL, parameters) == 1;
    }

    @Override
    public CompletionAnalyticsSnapshot summarize(UUID userId, int courseLimit) {
        var parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("courseLimit", courseLimit);
        var rows = jdbc.query(SUMMARY_SQL, parameters, this::mapRow);
        if (rows.isEmpty()) {
            return CompletionAnalyticsSnapshot.empty();
        }
        SummaryRow first = rows.getFirst();
        return new CompletionAnalyticsSnapshot(
                first.totalCompletedLessons(),
                first.totalCourses(),
                first.overallLastCompletedAt(),
                rows.stream().map(SummaryRow::course).toList()
        );
    }

    private SummaryRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SummaryRow(
                new CourseCompletionAggregate(
                        resultSet.getObject("course_id", UUID.class),
                        resultSet.getLong("completed_lessons"),
                        resultSet.getTimestamp("last_completed_at").toInstant()
                ),
                resultSet.getLong("total_completed_lessons"),
                resultSet.getLong("total_courses"),
                resultSet.getTimestamp("overall_last_completed_at").toInstant()
        );
    }

    private record SummaryRow(
            CourseCompletionAggregate course,
            long totalCompletedLessons,
            long totalCourses,
            java.time.Instant overallLastCompletedAt
    ) {
    }
}
