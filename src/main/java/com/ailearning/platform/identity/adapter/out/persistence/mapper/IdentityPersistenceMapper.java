package com.ailearning.platform.identity.adapter.out.persistence.mapper;

import com.ailearning.platform.identity.adapter.out.persistence.jpa.entity.RefreshSessionJpaEntity;
import com.ailearning.platform.identity.adapter.out.persistence.jpa.entity.RoleJpaEntity;
import com.ailearning.platform.identity.adapter.out.persistence.jpa.entity.UserJpaEntity;
import com.ailearning.platform.identity.domain.model.RefreshSession;
import com.ailearning.platform.identity.domain.model.User;

public final class IdentityPersistenceMapper {
    private IdentityPersistenceMapper() {
    }

    public static User toDomain(UserJpaEntity entity) {
        return new User(entity.getId(), entity.getEmail(), entity.getPasswordHash(), entity.getDisplayName(),
                entity.getStatus(), entity.getRoles().stream().map(RoleJpaEntity::getCode).collect(java.util.stream.Collectors.toSet()));
    }

    public static RefreshSession toDomain(RefreshSessionJpaEntity entity) {
        return new RefreshSession(entity.getId(), toDomain(entity.getUser()), entity.getTokenHash(),
                entity.getExpiresAt(), entity.getRevokedAt(), entity.getCreatedAt());
    }
}
