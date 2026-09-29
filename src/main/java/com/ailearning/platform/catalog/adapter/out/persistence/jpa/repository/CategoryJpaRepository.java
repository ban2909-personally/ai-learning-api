package com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity.CategoryJpaEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {
    @Query(
            value =
                    "SELECT c.* FROM categories c WHERE EXISTS (SELECT 1 FROM courses p WHERE"
                            + " p.category_id=c.id AND p.status='PUBLISHED') ORDER BY"
                            + " c.display_order,c.name",
            nativeQuery = true)
    List<CategoryJpaEntity> findPublishedCategories();

    List<CategoryJpaEntity> findAllByOrderByDisplayOrderAscNameAsc();
}
