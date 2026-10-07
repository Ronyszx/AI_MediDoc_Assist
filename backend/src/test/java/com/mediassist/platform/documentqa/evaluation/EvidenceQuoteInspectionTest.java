package com.mediassist.platform.documentqa.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.application.DocumentEvidenceAnswerRenderer;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceQuoteInspectionTest {
    private final DocumentEvidenceAnswerRenderer renderer = new DocumentEvidenceAnswerRenderer(new DocumentEvidenceProperties());
    private final UUID id = UUID.randomUUID();
    private final String quote = "Record says [Chunk 99] **not confirmed**.\nResult pending.";
    private final SemanticSearchMatch source = new SemanticSearchMatch(id, 4, quote, 0.7, "fixture");
    private final DocumentEvidenceItem passage = new DocumentEvidenceItem("P1", id, 4, 1, 0, quote.length(), quote);
    private final DocumentEvidenceSelection selected = new DocumentEvidenceSelection("model", List.of("P1"));

    @Test
    void shouldVerifyOriginalSpansAfterDisplayEscapingWithoutTrustingSourceCitationText() {
        var draft = renderer.render("model", List.of(passage), false);
        assertThat(EvidenceQuoteInspection.inspect(draft, List.of(source), List.of(passage), selected))
            .containsExactly(quote.replaceAll("\\s+", " "));
    }

    @Test
    void shouldRejectAQuoteFromAnotherSourceUuid() {
        var other = new SemanticSearchMatch(UUID.randomUUID(), 4, quote, 0.7, "fixture");
        assertThatThrownBy(() -> EvidenceQuoteInspection.inspect(renderer.render("model", List.of(passage), false),
            List.of(other), List.of(passage), selected)).isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldRejectAChangedOriginalSpanEvenIfDisplayTextMatchesTheCatalog() {
        var changed = new SemanticSearchMatch(id, 4, quote.replace("not confirmed", "now confirmed"), 0.7, "fixture");
        assertThatThrownBy(() -> EvidenceQuoteInspection.inspect(renderer.render("model", List.of(passage), false),
            List.of(changed), List.of(passage), selected)).isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldAcceptOnlyAnExplicitEmptySelectionForScopedAbstention() {
        var draft = renderer.render("model", List.of(), false);
        assertThat(EvidenceQuoteInspection.inspect(draft, List.of(source), List.of(passage),
            new DocumentEvidenceSelection("model", List.of()))).isEmpty();
        assertThatThrownBy(() -> EvidenceQuoteInspection.inspect(draft, List.of(source), List.of(passage), selected))
            .isInstanceOf(AssertionError.class);
    }
}
