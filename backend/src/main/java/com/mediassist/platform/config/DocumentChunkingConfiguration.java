package com.mediassist.platform.config;

import com.mediassist.platform.documentchunk.infrastructure.chunking.DocumentChunkingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentChunkingProperties.class)
public class DocumentChunkingConfiguration {
}
