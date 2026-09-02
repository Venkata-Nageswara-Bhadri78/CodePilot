package com.developer.copilot.common.storage.config;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    static final int CONNECT_TIMEOUT_SECONDS = 10;
    static final int IO_TIMEOUT_SECONDS = 30;

    @Bean
    public MinioClient minioClient(StorageProperties properties) {
        validateProvider(properties.getProvider());

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(
                        properties.getAccessKey(),
                        properties.getSecretKey()
                )
                .httpClient(httpClient)
                .build();
    }

    /**
     * {@code minio} and S3-compatible {@code s3} both use {@link MinioClient}. Anything else
     * is a misconfiguration — there is no second implementation.
     */
    static void validateProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("minio") && !normalized.equals("s3")) {
            throw new IllegalStateException("storage.provider must be minio or s3.");
        }
    }
}
