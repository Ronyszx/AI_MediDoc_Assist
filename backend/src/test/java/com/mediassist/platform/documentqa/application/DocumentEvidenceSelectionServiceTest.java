package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediassist.platform.documentqa.domain.DocumentEvidenceAssessment;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceRating;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentEvidenceSelectionServiceTest {
    private final DocumentEvidenceExtractor extractor = mock(DocumentEvidenceExtractor.class);
    private final DocumentEvidenceScorer scorer = mock(DocumentEvidenceScorer.class);
    private final DocumentEvidenceProperties settings = new DocumentEvidenceProperties();
    private final DocumentEvidenceSelectionService service = new DocumentEvidenceSelectionService(extractor, scorer, settings);

    @Test
    void shouldPreserveTheSingleCallWhenBatchSelectionIsDisabled() {
        var passages = passages(20);
        var expected = new DocumentEvidenceSelection("model", List.of("P20"));
        when(extractor.selectEvidence("Question", passages)).thenReturn(expected);
        assertThat(service.selectEvidence("Question", passages)).isSameAs(expected);
        verify(extractor).selectEvidence("Question", passages);
        verifyNoInteractions(scorer);
    }

    @Test
    void shouldGradeEveryPassageEvenInASmallEnabledCatalog() {
        settings.setBatchSelectionEnabled(true);
        var passages = passages(8);
        when(scorer.assessEvidence("Question", passages)).thenReturn(assessment("model", passages, List.of()));
        assertThat(service.selectEvidence("Question", passages).passageIds()).isEmpty();
        verify(scorer).assessEvidence("Question", passages);
        verifyNoInteractions(extractor);
    }

    @Test
    void shouldRejectAnEmptyEnabledCatalogWithoutCallingTheProvider() {
        settings.setBatchSelectionEnabled(true);
        assertThatThrownBy(() -> service.selectEvidence("Question", List.of()))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("No evidence passages");
        verifyNoInteractions(extractor, scorer);
    }

    @Test
    void shouldMergeEqualGradesInCatalogOrderInsteadOfProviderOrder() {
        settings.setBatchSelectionEnabled(true);
        var passages = passages(8);
        var reversedRatings = passages.reversed().stream()
            .map(item -> new DocumentEvidenceRating(item.passageId(), 3)).toList();
        when(scorer.assessEvidence("Question", passages)).thenReturn(new DocumentEvidenceAssessment("model", reversedRatings));
        assertThat(service.selectEvidence("Question", passages).passageIds())
            .containsExactly("P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8");
    }

    @Test
    void shouldEvaluateEveryBatchPreservingOriginalIdsAndQuotes() {
        settings.setBatchSelectionEnabled(true);
        var passages = passages(18);
        when(scorer.assessEvidence(eq("Question"), anyList())).thenAnswer(invocation -> {
            List<DocumentEvidenceItem> batch = invocation.getArgument(1);
            return assessment("model", batch, List.of(batch.getLast().passageId()));
        });
        var result = service.selectEvidence("Question", passages);
        assertThat(result.passageIds()).containsExactly("P8", "P16", "P18");
        assertThat(result.limited()).isFalse();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentEvidenceItem>> batches = ArgumentCaptor.forClass(List.class);
        verify(scorer, times(3)).assessEvidence(eq("Question"), batches.capture());
        assertThat(batches.getAllValues()).containsExactly(passages.subList(0, 8), passages.subList(8, 16), passages.subList(16, 18));
    }

    @Test
    void shouldAllocateEqualGradeTurnsToLaterBatchesAndMarkTheGlobalCap() {
        settings.setBatchSelectionEnabled(true);
        settings.setMaxItems(3);
        when(scorer.assessEvidence(eq("Question"), anyList())).thenAnswer(invocation -> {
            List<DocumentEvidenceItem> batch = invocation.getArgument(1);
            return assessment("model", batch, batch.stream().map(DocumentEvidenceItem::passageId).toList());
        });
        var result = service.selectEvidence("Question", passages(20));
        assertThat(result.passageIds()).containsExactly("P1", "P9", "P17");
        assertThat(result.limited()).isTrue();
    }

    @Test
    void shouldRankDirectStatementsBeforeSupportingStatementsAndIgnoreBackground() {
        settings.setBatchSelectionEnabled(true);
        when(scorer.assessEvidence(eq("Question"), anyList())).thenAnswer(invocation -> {
            List<DocumentEvidenceItem> batch = invocation.getArgument(1);
            return new DocumentEvidenceAssessment("model", batch.stream().map(item -> new DocumentEvidenceRating(
                item.passageId(), item.passageId().equals("P9") ? 3 : item.passageId().equals("P1") ? 2 : 1)).toList());
        });
        assertThat(service.selectEvidence("Question", passages(16)).passageIds()).containsExactly("P9", "P1");
    }

    @Test
    void shouldRejectAnIdFromAnotherBatchEvenIfItExistsInTheFullCatalog() {
        settings.setBatchSelectionEnabled(true);
        when(scorer.assessEvidence(eq("Question"), anyList())).thenAnswer(invocation -> {
            List<DocumentEvidenceItem> batch = invocation.getArgument(1);
            return new DocumentEvidenceAssessment("model", batch.stream().map(item -> new DocumentEvidenceRating(
                item.passageId().equals("P1") ? "P9" : item.passageId(), 3)).toList());
        });
        assertThatThrownBy(() -> service.selectEvidence("Question", passages(16)))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("outside its batch");
        verify(scorer).assessEvidence(eq("Question"), anyList());
    }

    @Test
    void shouldRejectModelChangesAcrossBatches() {
        settings.setBatchSelectionEnabled(true);
        var passages = passages(16);
        when(scorer.assessEvidence(eq("Question"), anyList()))
            .thenReturn(assessment("model-a", passages.subList(0, 8), List.of("P1")))
            .thenReturn(assessment("model-b", passages.subList(8, 16), List.of("P9")));
        assertThatThrownBy(() -> service.selectEvidence("Question", passages))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("model changed");
    }

    @Test
    void shouldRejectRepeatedRatingsBeforeMerging() {
        settings.setBatchSelectionEnabled(true);
        when(scorer.assessEvidence(eq("Question"), anyList())).thenAnswer(invocation -> {
            List<DocumentEvidenceItem> batch = invocation.getArgument(1);
            return new DocumentEvidenceAssessment("model", batch.stream().map(item -> new DocumentEvidenceRating("P1", 3)).toList());
        });
        assertThatThrownBy(() -> service.selectEvidence("Question", passages(16)))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("repeated");
    }

    @Test
    void shouldRejectMissingRatingsBeforeMerging() {
        settings.setBatchSelectionEnabled(true);
        when(scorer.assessEvidence(eq("Question"), anyList())).thenReturn(new DocumentEvidenceAssessment("model", List.of()));
        assertThatThrownBy(() -> service.selectEvidence("Question", passages(16)))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("cover every");
    }

    @Test
    void shouldRejectInvalidGradesBeforeMerging() {
        settings.setBatchSelectionEnabled(true);
        when(scorer.assessEvidence(eq("Question"), anyList())).thenAnswer(invocation -> {
            List<DocumentEvidenceItem> batch = invocation.getArgument(1);
            return new DocumentEvidenceAssessment("model", batch.stream().map(item -> new DocumentEvidenceRating(item.passageId(), 4)).toList());
        });
        assertThatThrownBy(() -> service.selectEvidence("Question", passages(16)))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("invalid");
    }

    @Test
    void shouldNotRenderAPartialSuccessWhenALaterProviderCallFails() {
        settings.setBatchSelectionEnabled(true);
        var failure = new LlmServiceUnavailableException("Unavailable");
        var passages = passages(16);
        when(scorer.assessEvidence(eq("Question"), anyList()))
            .thenReturn(assessment("model", passages.subList(0, 8), List.of("P1"))).thenThrow(failure);
        assertThatThrownBy(() -> service.selectEvidence("Question", passages)).isSameAs(failure);
        verifyNoInteractions(extractor);
    }

    private DocumentEvidenceAssessment assessment(String model, List<DocumentEvidenceItem> batch, List<String> relevantIds) {
        return new DocumentEvidenceAssessment(model, batch.stream().map(item -> new DocumentEvidenceRating(
            item.passageId(), relevantIds.contains(item.passageId()) ? 3 : 0)).toList());
    }

    private List<DocumentEvidenceItem> passages(int count) {
        UUID sourceId = UUID.randomUUID();
        return IntStream.rangeClosed(1, count).mapToObj(index -> new DocumentEvidenceItem("P" + index,
            sourceId, 0, 1, 0, 8, "Evidence")).toList();
    }
}
