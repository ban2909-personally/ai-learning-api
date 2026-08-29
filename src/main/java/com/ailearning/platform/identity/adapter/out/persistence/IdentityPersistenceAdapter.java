package com.ailearning.platform.identity.adapter.out.persistence;

import com.ailearning.platform.identity.adapter.out.persistence.jpa.entity.RefreshSessionJpaEntity;
import com.ailearning.platform.identity.adapter.out.persistence.jpa.entity.RoleJpaEntity;
import com.ailearning.platform.identity.adapter.out.persistence.jpa.entity.UserJpaEntity;
import com.ailearning.platform.identity.adapter.out.persistence.jpa.repository.RefreshSessionJpaRepository;
import com.ailearning.platform.identity.adapter.out.persistence.jpa.repository.RoleJpaRepository;
import com.ailearning.platform.identity.adapter.out.persistence.jpa.repository.UserJpaRepository;
import com.ailearning.platform.identity.adapter.out.persistence.mapper.IdentityPersistenceMapper;
import com.ailearning.platform.identity.application.port.out.RefreshSessionStore;
import com.ailearning.platform.identity.application.port.out.UserStore;
import com.ailearning.platform.identity.domain.model.RefreshSession;
import com.ailearning.platform.identity.domain.model.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class IdentityPersistenceAdapter implements UserStore, RefreshSessionStore {
    private final UserJpaRepository users;
    private final RoleJpaRepository roles;
    private final RefreshSessionJpaRepository sessions;

    public IdentityPersistenceAdapter(UserJpaRepository users, RoleJpaRepository roles,
                                      RefreshSessionJpaRepository sessions) {
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
    }

    @Override public boolean existsById(UUID id) { return users.existsById(id); }
    @Override public boolean existsByEmail(String email) { return users.existsByEmail(email); }
    @Override public Optional<User> findById(UUID id) { return users.findById(id).map(IdentityPersistenceMapper::toDomain); }
    @Override public Optional<User> findByEmail(String email) { return users.findByEmail(email).map(IdentityPersistenceMapper::toDomain); }

    @Override
    public User createStudent(UUID id, String email, String passwordHash, String displayName, Instant now) {
        RoleJpaEntity student = roles.findByCode("STUDENT")
                .orElseThrow(() -> new IllegalStateException("STUDENT role was not seeded"));
        UserJpaEntity saved = users.saveAndFlush(new UserJpaEntity(id, email, passwordHash, displayName, student, now));
        return IdentityPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<RefreshSession> findActiveForUpdate(String tokenHash) {
        return sessions.findByTokenHashAndRevokedAtIsNull(tokenHash).map(IdentityPersistenceMapper::toDomain);
    }

    @Override
    public void create(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        UserJpaEntity user = users.getReferenceById(userId);
        sessions.save(new RefreshSessionJpaEntity(id, user, tokenHash, expiresAt, createdAt));
    }

    @Override
    public void revoke(UUID id, Instant revokedAt) {
        RefreshSessionJpaEntity session = sessions.findById(id)
                .orElseThrow(() -> new IllegalStateException("Refresh session disappeared while locked"));
        session.revoke(revokedAt);
    }
}
