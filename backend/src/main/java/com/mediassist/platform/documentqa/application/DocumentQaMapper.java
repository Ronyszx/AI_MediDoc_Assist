package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionResponse;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionSourceResponse;
import com.mediassist.platform.documentqa.domain.DocumentQuestionAnswer;
import com.mediassist.platform.documentqa.domain.DocumentQuestionSource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentQaMapper {

    private static final int SOURCE_PREVIEW_LENGTH = 240;

    public DocumentQuestionSource toSource(SemanticSearchMatch match) {
        return new DocumentQuestionSource(
            match.chunkId(),
            match.chunkIndex(),
            match.similarityScore(),
            preview(match.chunkText())
        );
    }

    public DocumentQuestionResponse toResponse(DocumentQuestionAnswer answer) {
        return new DocumentQuestionResponse(
            answer.documentId(),
            answer.question(),
            answer.answer(),
            answer.modelName(),
            answer.sources().stream()
                .map(this::toSourceResponse)
                .toList(),
            answer.answeredAt()
        );
    }

    private DocumentQuestionSourceResponse toSourceResponse(DocumentQuestionSource source) {
        return new DocumentQuestionSourceResponse(
            source.chunkId(),
            source.chunkIndex(),
            source.similarityScore(),
            source.preview()
        );
    }

    private String preview(String text) {
        String normalizedText = text.replaceAll("\\s+", " ").trim();
        if (normalizedText.length() <= SOURCE_PREVIEW_LENGTH) {
            return normalizedText;
        }

        return normalizedText.substring(0, SOURCE_PREVIEW_LENGTH).trim() + "...";
    }
}
