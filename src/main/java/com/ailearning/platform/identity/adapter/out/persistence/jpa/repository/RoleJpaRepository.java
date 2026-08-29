package com.ailearning.platform.identity.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.identity.adapter.out.persistence.jpa.entity.RoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

    Optional<RoleJpaEntity> findByCode(String code);
}
