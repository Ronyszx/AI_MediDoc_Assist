package com.mediassist.platform.config;

import com.mediassist.platform.document.infrastructure.storage.DocumentStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentStorageProperties.class)
public class DocumentStorageConfiguration {
}
