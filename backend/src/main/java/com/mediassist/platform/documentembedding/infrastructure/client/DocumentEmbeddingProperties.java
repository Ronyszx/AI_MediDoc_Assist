package com.mediassist.platform.documentembedding.infrastructure.client;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mediassist.embedding")
public class DocumentEmbeddingProperties {

    @NotBlank
    private String endpointUrl = "http://localhost:8001/api/v1/embeddings";

    @NotBlank
    private String modelName = "BAAI/bge-m3";

    @Min(1)
    private int dimensions = 1024;

    @Min(1)
    private int batchSize = 32;
}
