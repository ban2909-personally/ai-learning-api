package com.ailearning.platform.identity.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.identity.adapter.out.persistence.jpa.entity.RefreshSessionJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionJpaRepository extends JpaRepository<RefreshSessionJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSessionJpaEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
