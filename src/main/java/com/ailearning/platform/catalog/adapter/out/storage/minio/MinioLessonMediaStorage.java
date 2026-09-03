package com.ailearning.platform.catalog.adapter.out.storage.minio;

import com.ailearning.platform.catalog.application.port.out.LessonMediaStorage;
import com.ailearning.platform.catalog.config.MinioStorageProperties;
import com.ailearning.platform.catalog.domain.model.LessonMediaAsset;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioLessonMediaStorage implements LessonMediaStorage {
    private final MinioClient client;
    private final MinioStorageProperties properties;
    private volatile boolean bucketReady;

    public MinioLessonMediaStorage(MinioClient client, MinioStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public LessonMediaAsset store(String objectKey, String contentType, long sizeBytes, InputStream content) {
        try {
            ensureBucket();
            var response = client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(content, sizeBytes, -1L)
                    .build());
            return new LessonMediaAsset(objectKey, contentType, sizeBytes, response.etag());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public InputStream open(String objectKey, long offset, long length) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .offset(offset)
                    .length(length)
                    .build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private synchronized void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
        }
        bucketReady = true;
    }

    private BusinessException unavailable(Exception cause) {
        var exception = new BusinessException(
                "media_storage_unavailable",
                ErrorType.SERVICE_UNAVAILABLE,
                "Kho nội dung hiện không khả dụng. Vui lòng thử lại sau."
        );
        exception.initCause(cause);
        return exception;
    }
}
