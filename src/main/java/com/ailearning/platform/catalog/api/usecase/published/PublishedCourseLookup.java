package com.ailearning.platform.catalog.api.usecase.published;

import com.ailearning.platform.catalog.api.contract.PublishedCourseView;
import java.util.Optional;
import java.util.UUID;

public interface PublishedCourseLookup {
    Optional<PublishedCourseView> findPublishedBySlug(String slug);
    Optional<PublishedCourseView> findPublishedById(UUID id);
}
