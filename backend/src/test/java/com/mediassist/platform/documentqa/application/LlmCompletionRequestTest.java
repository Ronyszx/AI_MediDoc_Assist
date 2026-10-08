package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmCompletionRequestTest {
    @Test
    void shouldPreserveExistingTextAndJsonConstructors() {
        var text = new LlmCompletionRequest("model", List.of(), 0.2, 800);
        var json = new LlmCompletionRequest("model", List.of(), 0, 512, LlmResponseFormat.JSON);
        assertThat(text.responseFormat()).isEqualTo(LlmResponseFormat.TEXT);
        assertThat(text.responseSchema()).isNull();
        assertThat(json.responseFormat()).isEqualTo(LlmResponseFormat.JSON);
        assertThat(json.responseSchema()).isNull();
    }

    @Test
    void shouldRejectASchemaOnAFreeTextRequest() {
        assertThatThrownBy(() -> new LlmCompletionRequest("model", List.of(), 0.2, 800,
            LlmResponseFormat.TEXT, Map.of("type", "object"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDefensivelyCopyMessagesAndTheSchemaEnvelope() {
        var messages = new ArrayList<LlmMessage>();
        var schema = new HashMap<String, Object>(Map.of("type", "object"));
        var request = new LlmCompletionRequest("model", messages, 0.2, 800, LlmResponseFormat.JSON, schema);
        messages.add(new LlmMessage("user", "Later change"));
        schema.put("type", "array");
        assertThat(request.messages()).isEmpty();
        assertThat(request.responseSchema()).containsEntry("type", "object");
        assertThatThrownBy(() -> request.responseSchema().put("new", true)).isInstanceOf(UnsupportedOperationException.class);
    }
}
