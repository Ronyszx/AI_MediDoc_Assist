package com.mediassist.platform.documentqa.infrastructure.client;

import com.mediassist.platform.documentqa.application.LlmSettings;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mediassist.llm")
public class DocumentQaProperties implements LlmSettings {

    @NotBlank
    @Pattern(regexp = "ollama|http")
    private String provider = "ollama";

    @NotBlank
    private String endpointUrl = "http://localhost:11434/api/chat";

    @NotBlank
    private String modelName = "qwen3.5:4b";

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature = 0.2;

    @Min(1)
    private int maxOutputTokens = 800;

    @Min(1024)
    private int contextWindowTokens = 8192;

    @NotNull
    @DurationMin(millis = 1)
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    @DurationMin(millis = 1)
    private Duration readTimeout = Duration.ofSeconds(120);
}
