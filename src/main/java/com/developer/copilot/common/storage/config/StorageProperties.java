package com.developer.copilot.common.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * Storage provider.
     * Example: minio, s3
     */
    private String provider;

    /**
     * Storage endpoint.
     * Example: http://localhost:9000
     */
    private String endpoint;

    /**
     * Storage access key.
     */
    private String accessKey;

    /**
     * Storage secret key.
     */
    private String secretKey;

    /**
     * Bucket name.
     */
    private String bucketName;

    /**
     * Automatically create bucket if it doesn't exist.
     */
    private boolean autoCreateBucket;
}