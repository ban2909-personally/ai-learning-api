package com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity.CategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {
    List<CategoryJpaEntity> findAllByOrderByDisplayOrderAscNameAsc();
}
