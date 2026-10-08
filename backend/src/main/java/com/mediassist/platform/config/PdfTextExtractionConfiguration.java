package com.mediassist.platform.config;

import com.mediassist.platform.documentextraction.infrastructure.extraction.PdfTextExtractionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PdfTextExtractionProperties.class)
public class PdfTextExtractionConfiguration {
}
