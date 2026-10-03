package com.mediassist.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.client.HttpLlmClient;
import com.mediassist.platform.documentqa.infrastructure.client.OllamaLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class DocumentQaConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(DocumentQaConfiguration.class, OllamaLlmClient.class, HttpLlmClient.class)
        .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void shouldSelectOnlyOllamaClientByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LlmClient.class);
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(OllamaLlmClient.class);
            assertThat(context.getBean(DocumentQaProperties.class).getModelName()).isEqualTo("qwen3.5:4b");
        });
    }

    @Test
    void shouldSelectOnlyCustomHttpClientWhenConfigured() {
        contextRunner.withPropertyValues(
            "mediassist.llm.provider=http",
            "mediassist.llm.endpoint-url=http://localhost:8002/api/v1/chat/completions",
            "mediassist.llm.model-name=custom-model"
        ).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LlmClient.class);
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(HttpLlmClient.class);
        });
    }

    @Test
    void shouldRejectUnsupportedProvider() {
        contextRunner.withPropertyValues("mediassist.llm.provider=unknown")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectUnboundedReadTimeout() {
        contextRunner.withPropertyValues("mediassist.llm.read-timeout=0s")
            .run(context -> assertThat(context).hasFailed());
    }
}
