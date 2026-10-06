package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentEvidenceValidatorTest {
    private final SemanticSearchMatch source = new SemanticSearchMatch(UUID.randomUUID(), 64, "Did not confirm the condition.", 0.7, "model");
    private final DocumentEvidenceItem valid = new DocumentEvidenceItem("P1", source.chunkId(), 64, 1, 0, source.chunkText().length(), source.chunkText());
    private final DocumentEvidenceValidator validator = new DocumentEvidenceValidator();

    @Test
    void shouldResolveServerTextAndPreserveNegationWithoutFuzzyMatching() {
        assertThat(validator.resolveAndValidate(List.of("P1"), List.of(valid), List.of(source))).containsExactly(valid);
        assertThat(validator.resolveAndValidate(List.of(), List.of(valid), List.of(source))).isEmpty();
    }

    @Test
    void shouldRejectUnknownAndRepeatedSelectionsRatherThanPartiallyAccept() {
        assertInvalid(List.of("P1", "P2"), List.of(valid), List.of(source));
        assertInvalid(List.of("P1", "P1"), List.of(valid), List.of(source));
    }

    @Test
    void shouldRejectChangedQuoteOrWhitespaceEvenWhenTheMeaningSeemsSimilar() {
        for (String quote : List.of("Did confirm the condition.", "did not confirm the condition.", "Did  not confirm the condition.")) {
            var altered = new DocumentEvidenceItem("P1", source.chunkId(), 64, 1, 0, source.chunkText().length(), quote);
            assertInvalid(List.of("P1"), List.of(altered), List.of(source));
        }
    }

    @Test
    void shouldRejectForeignSourcesAndWrongCitationPositionsOrIndexes() {
        for (var altered : List.of(
            new DocumentEvidenceItem("P1", UUID.randomUUID(), 64, 1, 0, source.chunkText().length(), source.chunkText()),
            new DocumentEvidenceItem("P1", source.chunkId(), 64, 64, 0, source.chunkText().length(), source.chunkText()),
            new DocumentEvidenceItem("P1", source.chunkId(), 65, 1, 0, source.chunkText().length(), source.chunkText()))) {
            assertInvalid(List.of("P1"), List.of(altered), List.of(source));
        }
    }

    @Test
    void shouldRejectInvalidOffsetRangesAndAmbiguousCatalogs() {
        for (var altered : List.of(
            new DocumentEvidenceItem("P1", source.chunkId(), 64, 1, -1, 2, "x"),
            new DocumentEvidenceItem("P1", source.chunkId(), 64, 1, 1, 1, ""),
            new DocumentEvidenceItem("P1", source.chunkId(), 64, 1, 0, 999, source.chunkText()))) {
            assertInvalid(List.of("P1"), List.of(altered), List.of(source));
        }
        assertInvalid(List.of("P1"), List.of(valid, valid), List.of(source));
        assertInvalid(List.of("P1"), List.of(valid), List.of(source, source));
    }

    private void assertInvalid(List<String> ids, List<DocumentEvidenceItem> items, List<SemanticSearchMatch> sources) {
        assertThatThrownBy(() -> validator.resolveAndValidate(ids, items, sources)).isInstanceOf(DocumentEvidenceException.class);
    }
}
