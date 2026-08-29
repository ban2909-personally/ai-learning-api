package com.ailearning.platform.catalog.application.port.out;

import com.ailearning.platform.catalog.application.query.CatalogQuery;
import com.ailearning.platform.catalog.domain.model.Category;
import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.sharedkernel.pagination.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogStore {
    PageResult<Course> findPublished(CatalogQuery query);
    Optional<Course> findPublishedBySlug(String slug);
    Optional<Course> findPublishedById(UUID id);
    List<Category> findCategories();
}
