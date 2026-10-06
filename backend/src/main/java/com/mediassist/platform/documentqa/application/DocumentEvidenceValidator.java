package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DocumentEvidenceValidator {
    public List<DocumentEvidenceItem> resolveAndValidate(List<String> selectedIds, List<DocumentEvidenceItem> passages,
                                                         List<SemanticSearchMatch> matches) {
        Map<String, DocumentEvidenceItem> catalog = new HashMap<>();
        for (DocumentEvidenceItem passage : passages) {
            if (catalog.putIfAbsent(passage.passageId(), passage) != null) {
                throw new DocumentEvidenceException("Duplicate evidence catalog ID");
            }
        }
        Map<UUID, SemanticSearchMatch> sources = new HashMap<>();
        for (SemanticSearchMatch match : matches) {
            if (sources.putIfAbsent(match.chunkId(), match) != null) {
                throw new DocumentEvidenceException("Duplicate source chunk ID");
            }
        }
        List<DocumentEvidenceItem> selected = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String id : selectedIds) {
            DocumentEvidenceItem passage = catalog.get(id);
            if (passage == null || !unique.add(id)) {
                throw new DocumentEvidenceException("Unknown or repeated selected passage");
            }
            SemanticSearchMatch source = sources.get(passage.chunkId());
            if (source == null || passage.chunkIndex() != source.chunkIndex()
                || passage.citationNumber() < 1 || passage.citationNumber() > matches.size()
                || !matches.get(passage.citationNumber() - 1).chunkId().equals(passage.chunkId())
                || passage.startOffset() < 0 || passage.endOffset() <= passage.startOffset()
                || passage.endOffset() > source.chunkText().length()
                || !source.chunkText().substring(passage.startOffset(), passage.endOffset()).equals(passage.quote())) {
                throw new DocumentEvidenceException("Evidence provenance or source span is invalid");
            }
            selected.add(passage);
        }
        return List.copyOf(selected);
    }
}
