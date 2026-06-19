package com.mediassist.platform.documentqa.infrastructure.client;

import com.mediassist.platform.documentqa.application.LlmSettings;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mediassist.llm")
public class DocumentQaProperties implements LlmSettings {

    @NotBlank
    private String endpointUrl = "http://localhost:8002/api/v1/chat/completions";

    @NotBlank
    private String modelName = "local-model-name";

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature = 0.2;

    @Min(1)
    private int maxOutputTokens = 800;
}
