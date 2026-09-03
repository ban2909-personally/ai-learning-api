package com.ailearning.platform.catalog.adapter.out.storage.minio;

import com.ailearning.platform.catalog.config.MinioStorageProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class MinioLessonMediaStorageIntegrationTest {
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin_test_password";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "minio/minio:RELEASE.2025-04-22T22-12-26Z"
    ))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Test
    void storesAndReadsOnlyRequestedRange() throws Exception {
        String endpoint = "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000));
        var properties = new MinioStorageProperties(endpoint, ACCESS_KEY, SECRET_KEY, "lesson-media-test");
        var storage = new MinioLessonMediaStorage(
                MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials(ACCESS_KEY, SECRET_KEY)
                        .build(),
                properties
        );
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);

        var stored = storage.store(
                "courses/course/lessons/lesson/media",
                "video/mp4",
                content.length,
                new ByteArrayInputStream(content)
        );

        assertEquals(content.length, stored.sizeBytes());
        try (var range = storage.open(stored.objectKey(), 3, 4)) {
            assertArrayEquals("3456".getBytes(StandardCharsets.UTF_8), range.readAllBytes());
        }
        storage.delete(stored.objectKey());
    }
}
