package com.ailearning.platform.community.adapter.out.storage.minio;

import com.ailearning.platform.community.application.port.out.CommunityMediaStorage;
import com.ailearning.platform.platform.configuration.storage.MinioStorageProperties;
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
public class MinioCommunityMediaStorage implements CommunityMediaStorage {
    private final MinioClient client;
    private final MinioStorageProperties properties;
    private volatile boolean bucketReady;

    public MinioCommunityMediaStorage(MinioClient client, MinioStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String store(String key, String type, long size, InputStream content) {
        try {
            ensureBucket();
            return client.putObject(
                            PutObjectArgs.builder()
                                    .bucket(properties.bucket())
                                    .object(key)
                                    .contentType(type)
                                    .stream(content, size, -1L)
                                    .build())
                    .etag();
        } catch (Exception failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public InputStream open(String key, long start, long length) {
        try {
            return client.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(key)
                            .offset(start)
                            .length(length)
                            .build());
        } catch (Exception failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(
                    RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());
        } catch (Exception failure) {
            throw unavailable(failure);
        }
    }

    private synchronized void ensureBucket() throws Exception {
        if (bucketReady) return;
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
        }
        bucketReady = true;
    }

    private BusinessException unavailable(Exception failure) {
        BusinessException exception =
                new BusinessException(
                        "community_storage_unavailable",
                        ErrorType.SERVICE_UNAVAILABLE,
                        "Kho media hiện không khả dụng. Vui lòng thử lại.");
        exception.initCause(failure);
        return exception;
    }
}
