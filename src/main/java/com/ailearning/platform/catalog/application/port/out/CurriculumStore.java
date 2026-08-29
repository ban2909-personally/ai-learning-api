package com.ailearning.platform.catalog.application.port.out;

import com.ailearning.platform.catalog.api.contract.CourseCurriculumView;
import java.util.Optional;

public interface CurriculumStore {
    Optional<CourseCurriculumView> findPublishedByCourseSlug(String courseSlug);
}
