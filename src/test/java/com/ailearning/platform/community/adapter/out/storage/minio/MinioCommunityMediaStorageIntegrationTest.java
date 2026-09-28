package com.ailearning.platform.community.adapter.out.storage.minio;

import static org.junit.jupiter.api.Assertions.*;

import com.ailearning.platform.platform.configuration.storage.MinioStorageProperties;

import io.minio.MinioClient;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Testcontainers(disabledWithoutDocker = true)
class MinioCommunityMediaStorageIntegrationTest {
    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(
                            "cgr.dev/chainguard/minio:latest@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1")
                    .withEnv("MINIO_ROOT_USER", "minioadmin")
                    .withEnv("MINIO_ROOT_PASSWORD", "minioadmin_test_password")
                    .withCommand("server", "/tmp/minio-data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Test
    void streamsRequestedBytesAndDeletesImmutableObject() throws Exception {
        String endpoint = "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000));
        var client =
                MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials("minioadmin", "minioadmin_test_password")
                        .build();
        var storage =
                new MinioCommunityMediaStorage(
                        client,
                        new MinioStorageProperties(
                                endpoint,
                                "minioadmin",
                                "minioadmin_test_password",
                                "community-media-test"));
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        assertFalse(
                storage.store("community/test", "video/mp4", 10, new ByteArrayInputStream(content))
                        .isBlank());
        try (var range = storage.open("community/test", 3, 4)) {
            assertArrayEquals("3456".getBytes(StandardCharsets.UTF_8), range.readAllBytes());
        }
        storage.delete("community/test");
        assertThrows(
                com.ailearning.platform.sharedkernel.error.BusinessException.class,
                () -> storage.open("community/test", 0, 10));
    }
}
