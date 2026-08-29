package com.ailearning.platform.catalog.adapter.in.web.controller;

import com.ailearning.platform.catalog.adapter.in.web.dto.response.*;
import com.ailearning.platform.catalog.api.usecase.CatalogUseCase;
import com.ailearning.platform.catalog.api.usecase.published.PublishedCurriculumLookup;
import com.ailearning.platform.catalog.application.query.CatalogQuery;
import com.ailearning.platform.catalog.domain.enums.CourseLevel;
import com.ailearning.platform.catalog.adapter.in.web.dto.response.PageResponse;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogUseCase catalog;
    private final PublishedCurriculumLookup curricula;
    public CatalogController(CatalogUseCase catalog, PublishedCurriculumLookup curricula) { this.catalog = catalog; this.curricula = curricula; }
    @GetMapping("/courses")
    PageResponse<CourseSummaryResponse> courses(@RequestParam(required=false) String search,
            @RequestParam(required=false) String category, @RequestParam(required=false) CourseLevel level,
            @RequestParam(required=false) BigDecimal minPrice, @RequestParam(required=false) BigDecimal maxPrice,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="12") int size) {
        return PageResponse.from(catalog.findPublishedCourses(new CatalogQuery(search, category, level, minPrice, maxPrice, page, size)), CourseSummaryResponse::from);
    }
    @GetMapping("/courses/{slug}") CourseDetailResponse course(@PathVariable String slug) { return CourseDetailResponse.from(catalog.findPublishedCourse(slug)); }
    @GetMapping("/courses/{slug}/curriculum") CourseCurriculumResponse curriculum(@PathVariable String slug) {
        return CourseCurriculumResponse.from(curricula.findPublishedCurriculum(slug));
    }
    @GetMapping("/categories") List<CategoryResponse> categories() { return catalog.findCategories().stream().map(CategoryResponse::from).toList(); }
}
