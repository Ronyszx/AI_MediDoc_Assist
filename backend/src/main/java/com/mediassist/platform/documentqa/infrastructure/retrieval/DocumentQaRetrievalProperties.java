package com.mediassist.platform.documentqa.infrastructure.retrieval;

import com.mediassist.platform.documentqa.application.DocumentQaRetrievalSettings;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mediassist.qa.retrieval")
public class DocumentQaRetrievalProperties implements DocumentQaRetrievalSettings {

    private boolean diversityEnabled = false;

    @Min(10)
    @Max(100)
    private int candidateCount = 20;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double relevanceWeight = 0.7;
}
