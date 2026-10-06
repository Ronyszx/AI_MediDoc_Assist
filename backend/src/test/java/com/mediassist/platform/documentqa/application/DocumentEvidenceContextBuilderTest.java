package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentEvidenceContextBuilderTest {
    private final DocumentEvidenceProperties settings = new DocumentEvidenceProperties();
    private final DocumentQaProperties llmSettings = new DocumentQaProperties();
    private final DocumentEvidenceContextBuilder builder = new DocumentEvidenceContextBuilder(
        new DocumentEvidencePromptBuilder(new ObjectMapper(), settings), settings, llmSettings);

    @Test
    void shouldKeepWholeParagraphsAndExactUtf16SourceOffsetsWithCrLfAndUnicode() {
        var source = source("  A \uD83D\uDE00 report.\r\nPending result. \r\n \r\n\tNo diagnosis confirmed.  ");
        var context = builder.build("Question", List.of(source));
        assertThat(context.limited()).isFalse();
        assertThat(context.passages()).hasSize(2);
        assertThat(context.passages().getFirst().quote()).isEqualTo("A \uD83D\uDE00 report.\r\nPending result.");
        assertThat(context.passages().get(1).quote()).isEqualTo("No diagnosis confirmed.");
        context.passages().forEach(item -> {
            assertThat(item.quote()).isEqualTo(source.chunkText().substring(item.startOffset(), item.endOffset()));
            assertThat(item.citationNumber()).isEqualTo(1);
            assertThat(item.chunkIndex()).isEqualTo(64);
            assertThat(item.chunkId()).isEqualTo(source.chunkId());
        });
    }

    @Test
    void shouldKeepSourceOrderAndUseCitationPositionsNotChunkIndexes() {
        var first = source("First paragraph.\n\nSecond paragraph.");
        var second = source("Other evidence.");
        var context = builder.build("Question", List.of(first, second));
        assertThat(context.passages()).extracting(item -> item.passageId()).containsExactly("P1", "P2", "P3");
        assertThat(context.passages().get(2).citationNumber()).isEqualTo(2);
    }

    @Test
    void shouldSkipOversizedParagraphsWithoutTruncatingAndRecordLimits() {
        settings.setMaxPassageCharacters(128);
        var context = builder.build("Question", List.of(source("not " + "x".repeat(150) + "\n\nA complete short passage.")));
        assertThat(context.limited()).isTrue();
        assertThat(context.passages()).extracting(item -> item.quote()).containsExactly("A complete short passage.");
    }

    @Test
    void shouldRespectCatalogCountAndPromptOutputReserve() {
        settings.setMaxPassages(1);
        var source = source("One paragraph.\n\nAnother paragraph.");
        var context = builder.build("Question", List.of(source));
        assertThat(context.passages()).hasSize(1);
        assertThat(context.limited()).isTrue();
        llmSettings.setContextWindowTokens(1024);
        assertThat(builder.build("Question", List.of(source)).passages()).isEmpty();
    }

    @Test
    void shouldNotTreatEmptyParagraphsAsMissingEvidence() {
        var context = builder.build("Question", List.of(source("\n\n \n\n")));
        assertThat(context.passages()).isEmpty();
        assertThat(context.limited()).isFalse();
    }

    private SemanticSearchMatch source(String text) {
        return new SemanticSearchMatch(UUID.randomUUID(), 64, text, 0.7, "model");
    }
}
