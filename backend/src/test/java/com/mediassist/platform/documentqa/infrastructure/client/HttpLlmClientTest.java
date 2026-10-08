package com.mediassist.platform.documentqa.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmMessage;
import com.mediassist.platform.documentqa.application.LlmResponseFormat;
import com.mediassist.platform.documentqa.application.LlmServiceUnavailableException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpLlmClientTest {
    @Test
    void shouldPreserveTheExistingTextCompletionContract() {
        var properties = new DocumentQaProperties();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(properties.getEndpointUrl()))
            .andRespond(withSuccess("{\"model\":\"model\",\"content\":\" Answer. \"}", MediaType.APPLICATION_JSON));
        var client = new HttpLlmClient(builder.build(), properties);
        assertThat(client.complete(new LlmCompletionRequest("model", List.of(new LlmMessage("user", "Question")),
            0.2, 800)).content()).isEqualTo("Answer.");
        server.verify();
    }

    @Test
    void shouldFailBeforeHttpInsteadOfSilentlyIgnoringAnUnsupportedSchema() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new HttpLlmClient(builder.build(), new DocumentQaProperties());
        assertThatThrownBy(() -> client.complete(new LlmCompletionRequest("model", List.of(), 0.2, 800,
            LlmResponseFormat.JSON, Map.of("type", "object"))))
            .isInstanceOf(LlmServiceUnavailableException.class).hasMessageContaining("does not support schema-constrained");
        server.verify();
    }
}
