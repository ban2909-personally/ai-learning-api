package com.ailearning.platform.catalog.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.catalog.cache.popular")
public record PopularCatalogCacheProperties(
        @NotBlank String keyPrefix,
        @NotNull @DurationMin(seconds = 1) Duration ttl
) {
}
