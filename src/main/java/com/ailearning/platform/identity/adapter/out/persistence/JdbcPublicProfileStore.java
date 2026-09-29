package com.ailearning.platform.identity.adapter.out.persistence;

import com.ailearning.platform.identity.api.contract.PublicProfile;
import com.ailearning.platform.identity.application.port.out.PublicProfileStore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPublicProfileStore implements PublicProfileStore {
    private static final RowMapper<PublicProfile> MAPPER =
            (rs, row) ->
                    new PublicProfile(rs.getObject("id", UUID.class), rs.getString("display_name"));
    private final JdbcTemplate jdbc;

    public JdbcPublicProfileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PublicProfile> search(String query, int page) {
        String literal = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jdbc.query(
                connection -> {
                    var statement =
                            connection.prepareStatement(
                                    """
SELECT id,display_name FROM users
WHERE status='ACTIVE' AND platform_search_key(display_name) LIKE platform_search_key(?)
ORDER BY CASE WHEN platform_search_key(display_name)=platform_search_key(?) THEN 0
  WHEN platform_search_key(display_name) LIKE platform_search_key(?) THEN 1 ELSE 2 END,
  platform_search_key(display_name),id LIMIT 21 OFFSET ?
""");
                    statement.setQueryTimeout(2);
                    statement.setString(1, "%" + literal + "%");
                    statement.setString(2, query);
                    statement.setString(3, literal + "%");
                    statement.setLong(4, (long) page * 20);
                    return statement;
                },
                MAPPER);
    }

    @Override
    public Optional<PublicProfile> findActive(UUID id) {
        return jdbc
                .query(
                        "SELECT id,display_name FROM users WHERE id=? AND status='ACTIVE'",
                        MAPPER,
                        id)
                .stream()
                .findFirst();
    }
}
