package com.ailearning.platform.catalog.api.usecase.published;

import com.ailearning.platform.catalog.api.contract.CourseCurriculumView;

public interface PublishedCurriculumLookup {
    CourseCurriculumView findPublishedCurriculum(String courseSlug);
}
