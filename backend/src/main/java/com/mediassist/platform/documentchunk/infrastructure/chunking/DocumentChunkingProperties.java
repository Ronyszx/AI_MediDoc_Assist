package com.mediassist.platform.documentchunk.infrastructure.chunking;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mediassist.chunking")
public class DocumentChunkingProperties {

    @Min(100)
    private int maxChunkSize = 1000;

    @Min(0)
    private int overlapSize = 150;

    @AssertTrue(message = "overlap-size must be smaller than max-chunk-size")
    public boolean isOverlapSizeValid() {
        return overlapSize < maxChunkSize;
    }
}
