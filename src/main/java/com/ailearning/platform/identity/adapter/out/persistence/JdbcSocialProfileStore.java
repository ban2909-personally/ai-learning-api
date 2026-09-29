package com.ailearning.platform.identity.adapter.out.persistence;

import com.ailearning.platform.identity.api.contract.SocialProfileDetails;
import com.ailearning.platform.identity.application.port.out.SocialProfileStore;
import com.ailearning.platform.identity.domain.valueobject.ProfileInformation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSocialProfileStore implements SocialProfileStore {
    private final JdbcTemplate jdbc;

    public JdbcSocialProfileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SocialProfileDetails> findActive(UUID id) {
        return jdbc
                .query(
                        """
                        SELECT u.id,u.display_name,COALESCE(p.bio,'') AS bio,
                            COALESCE(p.location,'') AS location,COALESCE(p.website,'') AS website,
                            COALESCE(p.cover_theme,'aurora') AS cover_theme
                        FROM users u LEFT JOIN identity_social_profiles p ON p.user_id=u.id
                        WHERE u.id=? AND u.status='ACTIVE'
                        """,
                        (rs, row) ->
                                new SocialProfileDetails(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("display_name"),
                                        rs.getString("bio"),
                                        rs.getString("location"),
                                        rs.getString("website"),
                                        rs.getString("cover_theme")),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public boolean save(UUID actor, ProfileInformation info) {
        return jdbc.update(
                        """
INSERT INTO identity_social_profiles(user_id,bio,location,website,cover_theme)
SELECT id,?,?,?,? FROM users WHERE id=? AND status='ACTIVE'
ON CONFLICT(user_id) DO UPDATE SET bio=EXCLUDED.bio,location=EXCLUDED.location,
    website=EXCLUDED.website,cover_theme=EXCLUDED.cover_theme,updated_at=CURRENT_TIMESTAMP
""",
                        info.bio(),
                        info.location(),
                        info.website(),
                        info.coverTheme(),
                        actor)
                == 1;
    }
}
