package com.ailearning.platform.catalog.api;

import com.ailearning.platform.catalog.application.CatalogQuery;
import com.ailearning.platform.catalog.application.CatalogService;
import com.ailearning.platform.catalog.domain.CourseLevel;
import com.ailearning.platform.shared.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/courses")
    PageResponse<CourseSummaryResponse> courses(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) CourseLevel level,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return catalogService.findPublishedCourses(
                new CatalogQuery(search, category, level, minPrice, maxPrice, page, size)
        );
    }

    @GetMapping("/courses/{slug}")
    CourseDetailResponse course(@PathVariable String slug) {
        return catalogService.findPublishedCourse(slug);
    }

    @GetMapping("/categories")
    List<CategoryResponse> categories() {
        return catalogService.findCategories();
    }
}
