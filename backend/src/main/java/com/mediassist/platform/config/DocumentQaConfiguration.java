package com.mediassist.platform.config;

import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentQaProperties.class)
public class DocumentQaConfiguration {
}
