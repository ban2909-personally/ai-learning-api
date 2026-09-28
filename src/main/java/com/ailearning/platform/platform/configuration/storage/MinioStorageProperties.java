package com.ailearning.platform.platform.configuration.storage;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage.minio")
public record MinioStorageProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket) {
    @Override
    public String toString() {
        return "MinioStorageProperties[endpoint=%s, accessKey=<redacted>, secretKey=<redacted>, bucket=%s]"
                .formatted(endpoint, bucket);
    }
}
