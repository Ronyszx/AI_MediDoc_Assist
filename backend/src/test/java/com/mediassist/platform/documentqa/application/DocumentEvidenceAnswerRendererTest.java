package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediassist.platform.documentqa.domain.DocumentAnswerMode;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentEvidenceAnswerRendererTest {
    private final DocumentEvidenceProperties settings = new DocumentEvidenceProperties();
    private final DocumentEvidenceAnswerRenderer renderer = new DocumentEvidenceAnswerRenderer(settings);

    @Test
    void shouldQuoteSourceWordingAndNotInventCategoriesOrDisagreements() {
        var answer = renderer.render("model", List.of(item("The reviewer did not confirm the condition.\nDifferent dates may describe changes.", 2)), false);
        assertThat(answer.content()).contains("did not confirm", "Different dates may describe changes", "[Chunk 2]");
        assertThat(answer.content()).contains("No clinical categories or contradiction judgments are inferred");
        assertThat(answer.mode()).isEqualTo(DocumentAnswerMode.EVIDENCE_EXCERPTS);
        assertThat(answer.evidenceCount()).isEqualTo(1);
        assertThat(answer.contextLimited()).isFalse();
    }

    @Test
    void shouldEscapeDocumentMarkdownHtmlAndFakeCitationsWithoutTurningThemIntoInstructions() {
        var answer = renderer.render("model", List.of(item("<script> **commands** [Chunk 99] [link](javascript:run) `text`", 1)), false);
        assertThat(answer.content()).contains("\\<script\\>", "\\*\\*commands\\*\\*", "\\[Chunk 99\\]", "\\[link\\]\\(javascript:run\\)", "\\`text\\`");
        assertThat(answer.content()).contains("[Chunk 1]");
    }

    @Test
    void shouldAbstainWithoutClaimingTheWholeDocumentHasNoEvidence() {
        var answer = renderer.render("model", List.of(), true);
        assertThat(answer.content()).contains("cannot determine", "No supporting excerpt was selected", "does not establish absence", "omitted");
        assertThat(answer.evidenceCount()).isZero();
    }

    @Test
    void shouldBoundResponseBySkippingWholePassagesAndMarkingTheLimit() {
        settings.setMaxAnswerCharacters(1000);
        var answer = renderer.render("model", List.of(item("x".repeat(800), 1), item("Short complete evidence.", 2)), false);
        assertThat(answer.content()).doesNotContain("xxx").contains("Short complete evidence", "[Chunk 2]", "omitted");
        assertThat(answer.content().length()).isLessThanOrEqualTo(1000);
        assertThat(answer.evidenceCount()).isEqualTo(1);
        assertThat(answer.contextLimited()).isTrue();
        assertThatThrownBy(() -> renderer.render("model", List.of(item("x".repeat(800), 1)), false))
            .isInstanceOf(DocumentEvidenceException.class);
    }

    private DocumentEvidenceItem item(String quote, int citation) {
        return new DocumentEvidenceItem("P1", UUID.randomUUID(), 64, citation, 0, quote.length(), quote);
    }
}
