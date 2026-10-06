package com.mediassist.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class DocumentEvidenceConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(DocumentQaConfiguration.class).withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void shouldRemainOptInUntilIndependentReview() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(DocumentEvidenceProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getMaxItems()).isEqualTo(12);
        });
    }

    @Test
    void shouldBindTheExplicitFlagAndLimits() {
        runner.withPropertyValues("mediassist.qa.evidence.enabled=true", "mediassist.qa.evidence.max-items=8")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(DocumentEvidenceProperties.class).isEnabled()).isTrue();
                assertThat(context.getBean(DocumentEvidenceProperties.class).getMaxItems()).isEqualTo(8);
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"max-items=0", "max-items=21", "max-passages=101", "max-passage-characters=127",
        "max-passage-characters=4001", "max-answer-characters=999", "max-answer-characters=16001"})
    void shouldRejectUnboundedSettings(String property) {
        runner.withPropertyValues("mediassist.qa.evidence." + property).run(context -> assertThat(context).hasFailed());
    }
}
