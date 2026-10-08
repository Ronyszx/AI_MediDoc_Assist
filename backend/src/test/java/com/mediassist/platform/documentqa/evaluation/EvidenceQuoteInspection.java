package com.mediassist.platform.documentqa.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.DocumentAnswerDraft;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class EvidenceQuoteInspection {
    private static final Pattern QUOTE = Pattern.compile("(?m)^- \"(.*)\" \\[Chunk ([0-9]+)]$");

    private EvidenceQuoteInspection() {
    }

    static List<String> inspect(DocumentAnswerDraft draft, List<SemanticSearchMatch> sources,
                                List<DocumentEvidenceItem> catalog, DocumentEvidenceSelection selection) {
        List<String> rendered = new ArrayList<>();
        var quotes = QUOTE.matcher(draft.content());
        while (quotes.find()) {
            String quote = unescape(quotes.group(1));
            int citation = Integer.parseInt(quotes.group(2));
            assertThat(citation).isBetween(1, sources.size());
            var source = sources.get(citation - 1);
            var passage = catalog.stream().filter(item -> selection.passageIds().contains(item.passageId())
                && item.citationNumber() == citation && item.quote().replaceAll("\\s+", " ").equals(quote))
                .findFirst().orElseThrow();
            assertThat(passage.chunkId()).isEqualTo(source.chunkId());
            assertThat(passage.chunkIndex()).isEqualTo(source.chunkIndex());
            assertThat(passage.quote()).isEqualTo(source.chunkText().substring(passage.startOffset(), passage.endOffset()));
            rendered.add(quote);
        }
        assertThat(rendered).hasSize(draft.evidenceCount());
        if (rendered.isEmpty()) {
            assertThat(selection.passageIds()).isEmpty();
            assertThat(draft.content()).contains("does not establish absence from the whole document");
        }
        return List.copyOf(rendered);
    }

    private static String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\\' && index + 1 < text.length() && "\\`*_{}[]()<>#+-!.|~".indexOf(text.charAt(index + 1)) >= 0) {
                character = text.charAt(++index);
            }
            result.append(character);
        }
        return result.toString();
    }
}
