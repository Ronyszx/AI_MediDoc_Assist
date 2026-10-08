package com.mediassist.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediassist.platform.documentextraction.infrastructure.extraction.PdfTextExtractionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PdfTextExtractionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(PdfTextExtractionConfiguration.class);

    @Test
    void shouldRegisterValidatedDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PdfTextExtractionProperties.class);
            assertThat(context.getBean(PdfTextExtractionProperties.class).getParagraphDropThreshold()).isEqualTo(3.0f);
        });
    }

    @Test
    void shouldAllowParagraphSpacingToBeConfiguredForDifferentPdfLayouts() {
        contextRunner.withPropertyValues("mediassist.extraction.paragraph-drop-threshold=4.0").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PdfTextExtractionProperties.class).getParagraphDropThreshold()).isEqualTo(4.0f);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "11.0"})
    void shouldRejectInvalidParagraphSpacing(String threshold) {
        contextRunner.withPropertyValues("mediassist.extraction.paragraph-drop-threshold=" + threshold)
            .run(context -> assertThat(context).hasFailed());
    }
}
