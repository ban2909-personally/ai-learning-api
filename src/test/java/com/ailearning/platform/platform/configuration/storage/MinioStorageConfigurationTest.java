package com.ailearning.platform.platform.configuration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.MinioClient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MinioStorageConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(MinioStorageConfiguration.class)
                    .withPropertyValues(
                            "app.storage.minio.endpoint=http://127.0.0.1:19000",
                            "app.storage.minio.access-key=configuration-test-access",
                            "app.storage.minio.secret-key=configuration-test-secret",
                            "app.storage.minio.bucket=configuration-test-bucket");

    @Test
    void bindsExistingSettingsAndCreatesOneClientWithoutConnectingToMinio() {
        runner.run(
                context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(MinioStorageProperties.class)
                            .hasSingleBean(MinioClient.class);
                    var properties = context.getBean(MinioStorageProperties.class);
                    assertThat(properties.endpoint()).isEqualTo("http://127.0.0.1:19000");
                    assertThat(properties.bucket()).isEqualTo("configuration-test-bucket");
                    assertThat(properties.accessKey()).isEqualTo("configuration-test-access");
                    assertThat(properties.secretKey()).isEqualTo("configuration-test-secret");
                    assertThat(context.getBean(MinioClient.class))
                            .isSameAs(context.getBean("minioClient"));
                });
    }

    @Test
    void rejectsBlankCredentialsBeforeCreatingTheClient() {
        for (String property : new String[] {"access-key", "secret-key"}) {
            runner.withPropertyValues("app.storage.minio." + property + "=")
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void doesNotExposeCredentialsInThePropertiesRepresentation() {
        var properties =
                new MinioStorageProperties(
                        "http://localhost:9000",
                        "private-access-value",
                        "private-secret-value",
                        "lesson-media");
        assertThat(properties.toString())
                .contains("<redacted>", "lesson-media")
                .doesNotContain("private-access-value", "private-secret-value");
    }
}
