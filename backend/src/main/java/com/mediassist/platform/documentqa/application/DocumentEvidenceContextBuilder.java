package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceContext;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DocumentEvidenceContextBuilder {
    // Atomic matching keeps a single CRLF from backtracking into two line breaks.
    private static final String LINE_BREAK = "(?>\\r\\n|[\\n\\r\\u0085\\u2028\\u2029])";
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile(
        LINE_BREAK + "[\\t ]*" + LINE_BREAK + "(?:[\\t ]*" + LINE_BREAK + ")*");
    private final DocumentEvidencePromptBuilder promptBuilder;
    private final DocumentEvidenceSettings settings;
    private final LlmSettings llmSettings;

    public DocumentEvidenceContextBuilder(DocumentEvidencePromptBuilder promptBuilder,
                                         DocumentEvidenceSettings settings, LlmSettings llmSettings) {
        this.promptBuilder = promptBuilder;
        this.settings = settings;
        this.llmSettings = llmSettings;
    }

    public DocumentEvidenceContext build(String question, List<SemanticSearchMatch> matches) {
        List<DocumentEvidenceItem> passages = new ArrayList<>();
        boolean limited = false;
        for (int index = 0; index < matches.size(); index++) {
            SemanticSearchMatch match = matches.get(index);
            Matcher breaks = PARAGRAPH_BREAK.matcher(match.chunkText());
            int start = 0;
            while (breaks.find()) {
                limited |= !addPassage(question, passages, match, index + 1, start, breaks.start());
                start = breaks.end();
            }
            limited |= !addPassage(question, passages, match, index + 1, start, match.chunkText().length());
        }
        return new DocumentEvidenceContext(passages, limited);
    }

    private boolean addPassage(String question, List<DocumentEvidenceItem> passages, SemanticSearchMatch match,
                               int citationNumber, int start, int end) {
        String text = match.chunkText();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return true;
        }
        if (passages.size() >= settings.getMaxPassages() || end - start > settings.getMaxPassageCharacters()) {
            return false;
        }

        DocumentEvidenceItem item = new DocumentEvidenceItem("P" + (passages.size() + 1), match.chunkId(), match.chunkIndex(),
            citationNumber, start, end, text.substring(start, end));
        List<DocumentEvidenceItem> proposed = new ArrayList<>(passages);
        proposed.add(item);
        // The same provisional byte-based estimate as retrieval, not an exact model tokenizer.
        long promptTokens = promptBuilder.buildMessages(question, proposed).stream()
            .mapToLong(message -> (message.content().getBytes(StandardCharsets.UTF_8).length + 1L) / 2 + 64).sum();
        if (promptTokens + llmSettings.getMaxOutputTokens() + 256 > llmSettings.getContextWindowTokens()) {
            return false;
        }
        passages.add(item);
        return true;
    }
}
