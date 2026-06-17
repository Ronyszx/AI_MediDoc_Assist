package com.mediassist.platform.config;

import com.mediassist.platform.documentembedding.infrastructure.client.DocumentEmbeddingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentEmbeddingProperties.class)
public class DocumentEmbeddingConfiguration {
}
