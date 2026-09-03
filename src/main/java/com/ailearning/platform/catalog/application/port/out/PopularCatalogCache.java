package com.ailearning.platform.catalog.application.port.out;

import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.sharedkernel.pagination.PageResult;

import java.util.Optional;

public interface PopularCatalogCache {
    Optional<PageResult<Course>> find(int pageSize);

    void put(int pageSize, PageResult<Course> page);
}
