package com.mediassist.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediassist.platform.documentqa.application.DocumentQaRetrievalSettings;
import com.mediassist.platform.documentqa.infrastructure.retrieval.DocumentQaRetrievalProperties;
import com.mediassist.platform.documentqa.infrastructure.retrieval.FacetRetrievalProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class DocumentQaRetrievalConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(DocumentQaConfiguration.class)
        .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void shouldKeepDiversityDisabledUntilEvaluated() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DocumentQaRetrievalSettings.class);
            DocumentQaRetrievalProperties properties = context.getBean(DocumentQaRetrievalProperties.class);
            assertThat(properties.isDiversityEnabled()).isFalse();
            assertThat(properties.getCandidateCount()).isEqualTo(20);
            assertThat(properties.getRelevanceWeight()).isEqualTo(0.7);
            assertThat(context.getBean(FacetRetrievalProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void shouldBindExplicitRetrievalSettings() {
        contextRunner.withPropertyValues(
            "mediassist.qa.retrieval.diversity-enabled=true",
            "mediassist.qa.retrieval.candidate-count=30",
            "mediassist.qa.retrieval.relevance-weight=0.5"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            DocumentQaRetrievalProperties properties = context.getBean(DocumentQaRetrievalProperties.class);
            assertThat(properties.isDiversityEnabled()).isTrue();
            assertThat(properties.getCandidateCount()).isEqualTo(30);
            assertThat(properties.getRelevanceWeight()).isEqualTo(0.5);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"candidate-count=9", "candidate-count=101", "relevance-weight=-0.1", "relevance-weight=1.1"})
    void shouldRejectUnboundedOrInvalidSettings(String property) {
        contextRunner.withPropertyValues("mediassist.qa.retrieval." + property)
            .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"max-facets=7", "max-facets=0", "original-candidate-count=21", "facet-candidate-count=11",
        "fusion-constant=0", "nomination-rank-limit=4", "max-evidence-tokens=0", "planner-output-tokens=1025"})
    void shouldRejectUnboundedFacetSettings(String property) {
        contextRunner.withPropertyValues("mediassist.qa.facets." + property)
            .run(context -> assertThat(context).hasFailed());
    }
}
