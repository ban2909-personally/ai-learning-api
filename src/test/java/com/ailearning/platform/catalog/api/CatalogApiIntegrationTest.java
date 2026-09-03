package com.ailearning.platform.catalog.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/catalog-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/catalog-test-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CatalogApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_catalog_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 1);
        registry.add("spring.data.redis.connect-timeout", () -> "100ms");
        registry.add("spring.data.redis.timeout", () -> "100ms");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void listsOnlyPublishedCoursesAndSupportsFiltering() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                        .queryParam("search", "Spring")
                        .queryParam("category", "backend")
                        .queryParam("level", "INTERMEDIATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("spring-boot-api-thuc-chien"))
                .andExpect(jsonPath("$.items[0].instructorName").value("Giảng viên Java"));
    }

    @Test
    void returnsPopularCatalogWhenRedisIsUnavailable() throws Exception {
        mockMvc.perform(get("/api/v1/courses").queryParam("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("spring-boot-api-thuc-chien"));
    }

    @Test
    void returnsPublishedCourseDetail() throws Exception {
        mockMvc.perform(get("/api/v1/courses/spring-boot-api-thuc-chien"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring Boot API thực chiến"))
                .andExpect(jsonPath("$.category.slug").value("backend"));
    }

    @Test
    void doesNotExposeDraftCourse() throws Exception {
        mockMvc.perform(get("/api/v1/courses/react-ban-nhap"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("course_not_found"));
    }

    @Test
    void rejectsInvalidPriceRange() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                        .queryParam("minPrice", "500000")
                        .queryParam("maxPrice", "100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_catalog_query"));
    }
}
