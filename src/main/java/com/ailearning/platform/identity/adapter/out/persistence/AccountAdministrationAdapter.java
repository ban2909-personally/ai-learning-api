package com.ailearning.platform.identity.adapter.out.persistence;

import com.ailearning.platform.identity.api.contract.AccountPage;
import com.ailearning.platform.identity.application.port.out.AccountAdministrationStore;
import com.ailearning.platform.identity.domain.model.ManagedAccount;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class AccountAdministrationAdapter implements AccountAdministrationStore {
    private static final String ACCOUNT_SELECT =
            """
SELECT u.id,u.email,u.display_name,u.status,u.created_at,
  COALESCE((SELECT string_agg(r.code, ',') FROM user_roles ur JOIN roles r ON r.id=ur.role_id
    WHERE ur.user_id=u.id),'') AS role_codes FROM users u
""";
    private static final RowMapper<ManagedAccount> MAPPER =
            (rs, row) ->
                    new ManagedAccount(
                            rs.getObject("id", UUID.class),
                            rs.getString("email"),
                            rs.getString("display_name"),
                            rs.getString("status"),
                            rs.getString("role_codes").isEmpty()
                                    ? Set.of()
                                    : Set.copyOf(
                                            Arrays.asList(rs.getString("role_codes").split(","))),
                            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;

    public AccountAdministrationAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public java.util.Map<String, Long> statistics() {
        var counts = new java.util.LinkedHashMap<String, Long>();
        counts.put("total", jdbc.queryForObject("SELECT count(*) FROM users", Long.class));
        counts.put(
                "active",
                jdbc.queryForObject(
                        "SELECT count(*) FROM users WHERE status='ACTIVE'", Long.class));
        jdbc.query(
                """
                SELECT r.code,count(u.id) AS total FROM roles r
                LEFT JOIN user_roles ur ON ur.role_id=r.id
                LEFT JOIN users u ON u.id=ur.user_id AND u.status='ACTIVE' GROUP BY r.code
                """,
                rs -> {
                    counts.put(rs.getString("code"), rs.getLong("total"));
                });
        return counts;
    }

    @Override
    public AccountPage list(String search, String role, int page, int size) {
        String filter =
                """
WHERE (lower(u.email) LIKE ? OR lower(u.display_name) LIKE ?)
  AND (? = '' OR EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
    WHERE ur.user_id=u.id AND r.code=?))
""";
        String term = "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
        var items =
                jdbc.query(
                        ACCOUNT_SELECT
                                + filter
                                + " ORDER BY u.created_at DESC,u.id LIMIT ? OFFSET ?",
                        MAPPER,
                        term,
                        term,
                        role,
                        role,
                        size,
                        (long) page * size);
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM users u " + filter,
                        Long.class,
                        term,
                        term,
                        role,
                        role);
        return new AccountPage(items, count, page, size);
    }

    @Override
    public Optional<ManagedAccount> find(UUID id) {
        return jdbc.query(ACCOUNT_SELECT + " WHERE u.id=?", MAPPER, id).stream().findFirst();
    }

    @Override
    public ManagedAccount create(String email, String name, String hash, String role) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO users(id,email,password_hash,display_name,status) VALUES"
                            + " (?,?,?,?,'ACTIVE')",
                    id,
                    email,
                    hash,
                    name);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "email_already_exists", ErrorType.CONFLICT, "Email đã được sử dụng.");
        }
        jdbc.update(
                "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?",
                id,
                role);
        return find(id).orElseThrow();
    }

    @Override
    public void lockAdministrators() {
        jdbc.queryForList(
                """
                SELECT u.id FROM users u WHERE EXISTS
                  (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
                   WHERE ur.user_id=u.id AND r.code='ADMIN') ORDER BY u.id FOR UPDATE
                """);
    }

    @Override
    public long activeAdministrators() {
        return jdbc.queryForObject(
                """
SELECT count(*) FROM users u WHERE u.status='ACTIVE' AND EXISTS
 (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='ADMIN')
""",
                Long.class);
    }

    @Override
    public void update(UUID id, String role, String status) {
        jdbc.update(
                "UPDATE users SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id);
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", id);
        jdbc.update(
                "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?",
                id,
                role);
        jdbc.update(
                "UPDATE refresh_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND"
                        + " revoked_at IS NULL",
                id);
    }
}
