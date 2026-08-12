package com.ailearning.platform.identity.infrastructure;

import com.ailearning.platform.identity.domain.RefreshSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSessionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSessionEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
