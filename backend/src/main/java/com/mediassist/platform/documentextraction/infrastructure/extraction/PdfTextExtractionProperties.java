package com.mediassist.platform.documentextraction.infrastructure.extraction;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mediassist.extraction")
public class PdfTextExtractionProperties {

    @DecimalMin("1.0")
    @DecimalMax("10.0")
    private float paragraphDropThreshold = 3.0f;
}
