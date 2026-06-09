package com.mediassist.platform.document.infrastructure.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mediassist.storage")
public class DocumentStorageProperties {

    @NotBlank
    private String documentsRoot = "./storage/documents";

    @NotEmpty
    private List<String> allowedExtensions = new ArrayList<>(List.of("pdf"));

    @NotEmpty
    private List<String> allowedContentTypes = new ArrayList<>(List.of("application/pdf"));
}
