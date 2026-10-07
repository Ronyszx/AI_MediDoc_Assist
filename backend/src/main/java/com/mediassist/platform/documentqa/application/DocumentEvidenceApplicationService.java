package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.DocumentAnswerDraft;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceContext;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentEvidenceApplicationService {
    private final DocumentEvidenceContextBuilder contextBuilder;
    private final DocumentEvidenceSelectionService selectionService;
    private final DocumentEvidenceValidator validator;
    private final DocumentEvidenceAnswerRenderer renderer;

    public DocumentEvidenceApplicationService(DocumentEvidenceContextBuilder contextBuilder, DocumentEvidenceSelectionService selectionService,
                                              DocumentEvidenceValidator validator, DocumentEvidenceAnswerRenderer renderer) {
        this.contextBuilder = contextBuilder;
        this.selectionService = selectionService;
        this.validator = validator;
        this.renderer = renderer;
    }

    public DocumentAnswerDraft generateAnswer(String question, List<SemanticSearchMatch> matches) {
        DocumentEvidenceContext context = contextBuilder.build(question, matches);
        if (context.passages().isEmpty()) {
            throw new DocumentEvidenceException("No complete evidence passage fits the context budget");
        }
        DocumentEvidenceSelection selection = selectionService.selectEvidence(question, context.passages());
        List<DocumentEvidenceItem> evidence = validator.resolveAndValidate(selection.passageIds(), context.passages(), matches);
        return renderer.render(selection.modelName(), evidence, context.limited() || selection.limited());
    }
}
