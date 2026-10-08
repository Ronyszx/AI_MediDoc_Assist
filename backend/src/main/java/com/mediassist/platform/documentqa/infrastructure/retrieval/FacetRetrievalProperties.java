package com.mediassist.platform.documentqa.infrastructure.retrieval;

import com.mediassist.platform.documentqa.application.FacetRetrievalSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mediassist.qa.facets")
public class FacetRetrievalProperties implements FacetRetrievalSettings {
    private boolean enabled = false;
    @Min(1) @Max(6)
    private int maxFacets = 6;
    @Min(10) @Max(20)
    private int originalCandidateCount = 20;
    @Min(1) @Max(10)
    private int facetCandidateCount = 10;
    @Min(1) @Max(1000)
    private int fusionConstant = 60;
    @Min(1) @Max(3)
    private int nominationRankLimit = 3;
    @Min(256) @Max(6000)
    private int maxEvidenceTokens = 4000;
    @Min(128) @Max(1024)
    private int plannerOutputTokens = 512;
}
