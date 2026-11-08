package com.swiftdrop.delivery.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration(proxyBeanMethods = false)
public class MultipartUploadConfiguration {

    @Bean
    MultipartConfigElement multipartConfigElement(@Value("${MAX_UPLOAD_MB:10}") long maxUploadMb) {
        if (maxUploadMb < 1) {
            throw new IllegalArgumentException("MAX_UPLOAD_MB must be a positive number");
        }
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(maxUploadMb));
        factory.setMaxRequestSize(DataSize.ofMegabytes(Math.addExact(maxUploadMb, 1)));
        return factory.createMultipartConfig();
    }
}