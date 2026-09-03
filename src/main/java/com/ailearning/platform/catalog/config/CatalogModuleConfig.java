package com.ailearning.platform.catalog.config;

import com.ailearning.platform.catalog.application.port.out.CatalogStore;
import com.ailearning.platform.catalog.application.port.out.CurriculumStore;
import com.ailearning.platform.catalog.application.port.out.LessonContentStore;
import com.ailearning.platform.catalog.application.port.out.LessonMediaCatalog;
import com.ailearning.platform.catalog.application.port.out.LessonMediaStorage;
import com.ailearning.platform.catalog.application.service.impl.CatalogService;
import com.ailearning.platform.catalog.application.service.impl.LessonMediaService;
import com.ailearning.platform.catalog.application.service.impl.LessonMediaDeliveryService;
import com.ailearning.platform.catalog.domain.policy.LessonMediaPolicy;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogModuleConfig {
    @Bean CatalogService catalogService(CatalogStore store, CurriculumStore curricula, LessonContentStore lessons) {
        return new CatalogService(store, curricula, lessons);
    }

    @Bean
    LessonMediaService lessonMediaService(
            LessonMediaCatalog catalog,
            LessonMediaStorage storage,
            LessonMediaProperties properties
    ) {
        var policy = new LessonMediaPolicy(
                properties.maxUploadSize().toBytes(),
                properties.allowedContentTypes()
        );
        return new LessonMediaService(catalog, storage, policy);
    }

    @Bean
    LessonMediaDeliveryService lessonMediaDeliveryService(
            LessonMediaCatalog catalog,
            LessonMediaStorage storage
    ) {
        return new LessonMediaDeliveryService(catalog, storage);
    }

    @Bean
    MinioClient minioClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
