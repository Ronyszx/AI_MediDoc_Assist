package com.mediassist.platform.documentqa.infrastructure.evidence;

import com.mediassist.platform.documentqa.application.DocumentEvidenceSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mediassist.qa.evidence")
public class DocumentEvidenceProperties implements DocumentEvidenceSettings {
    private boolean enabled = false;
    @Min(1) @Max(20)
    private int maxItems = 12;
    @Min(1) @Max(100)
    private int maxPassages = 80;
    @Min(128) @Max(4000)
    private int maxPassageCharacters = 1200;
    @Min(1000) @Max(16000)
    private int maxAnswerCharacters = 6000;
}
