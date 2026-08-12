package com.ailearning.platform.catalog.infrastructure;

import com.ailearning.platform.catalog.domain.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {
    List<CategoryEntity> findAllByOrderByDisplayOrderAscNameAsc();
}
