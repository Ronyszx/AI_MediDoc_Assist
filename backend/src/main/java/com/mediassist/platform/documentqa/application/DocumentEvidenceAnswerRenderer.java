package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentqa.domain.DocumentAnswerDraft;
import com.mediassist.platform.documentqa.domain.DocumentAnswerMode;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentEvidenceAnswerRenderer {
    private static final String HEADER = "Selected document excerpts (not an independent diagnosis or medical advice):\n\n";
    private static final String FOOTER = "\nThese excerpts cover only retrieved context and may not answer every part of the question. "
        + "No clinical categories or contradiction judgments are inferred.";
    private static final String LIMIT_NOTICE = "\nSome evidence was omitted because of context or response limits.";
    private static final String NO_EVIDENCE = "I cannot determine this from the retrieved document context. "
        + "No supporting excerpt was selected; this does not establish absence from the whole document.";
    private final DocumentEvidenceSettings settings;

    public DocumentEvidenceAnswerRenderer(DocumentEvidenceSettings settings) {
        this.settings = settings;
    }

    public DocumentAnswerDraft render(String modelName, List<DocumentEvidenceItem> evidence, boolean contextLimited) {
        if (evidence.isEmpty()) {
            return new DocumentAnswerDraft(NO_EVIDENCE + (contextLimited ? LIMIT_NOTICE : ""),
                modelName, DocumentAnswerMode.EVIDENCE_EXCERPTS, 0, contextLimited);
        }

        StringBuilder answer = new StringBuilder(HEADER);
        int included = 0;
        boolean limited = contextLimited;
        for (DocumentEvidenceItem item : evidence) {
            String excerpt = "- \"" + escapeMarkdown(item.quote()) + "\" [Chunk " + item.citationNumber() + "]\n\n";
            if (answer.length() + excerpt.length() + FOOTER.length() + LIMIT_NOTICE.length() > settings.getMaxAnswerCharacters()) {
                limited = true;
                continue;
            }
            answer.append(excerpt);
            included++;
        }
        if (included == 0) {
            throw new DocumentEvidenceException("No selected passage fits the response budget");
        }
        answer.append(FOOTER);
        if (limited) {
            answer.append(LIMIT_NOTICE);
        }
        return new DocumentAnswerDraft(answer.toString(), modelName, DocumentAnswerMode.EVIDENCE_EXCERPTS, included, limited);
    }

    private String escapeMarkdown(String text) {
        String normalized = text.replaceAll("\\s+", " ");
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if ("\\`*_{}[]()<>#+-!.|~".indexOf(character) >= 0) {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
