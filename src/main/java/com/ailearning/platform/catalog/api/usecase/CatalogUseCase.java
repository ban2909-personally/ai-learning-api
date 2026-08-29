package com.ailearning.platform.catalog.api.usecase;

import com.ailearning.platform.catalog.application.query.CatalogQuery;
import com.ailearning.platform.catalog.domain.model.Category;
import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.sharedkernel.pagination.PageResult;
import java.util.List;

public interface CatalogUseCase {
    PageResult<Course> findPublishedCourses(CatalogQuery query);
    Course findPublishedCourse(String slug);
    List<Category> findCategories();
}
