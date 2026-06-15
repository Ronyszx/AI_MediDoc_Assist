package com.mediassist.platform.documentextraction.application;

import com.mediassist.platform.documentextraction.api.dto.DocumentExtractionResponse;
import com.mediassist.platform.documentextraction.api.dto.DocumentExtractionTextResponse;
import com.mediassist.platform.documentextraction.domain.DocumentExtraction;
import com.mediassist.platform.documentextraction.domain.ExtractionStatus;
import org.springframework.stereotype.Component;

@Component
public class DocumentExtractionMapper {

    public DocumentExtractionResponse toResponse(DocumentExtraction extraction) {
        return new DocumentExtractionResponse(
            extraction.getId(),
            extraction.getDocument().getId(),
            extraction.getExtractionStatus(),
            extraction.getPageCount(),
            extraction.getCreatedAt(),
            extraction.getUpdatedAt()
        );
    }

    public DocumentExtractionTextResponse toTextResponse(DocumentExtraction extraction) {
        return new DocumentExtractionTextResponse(
            extraction.getId(),
            extraction.getDocument().getId(),
            extraction.getExtractionStatus(),
            extraction.getPageCount(),
            extraction.getExtractedText(),
            extraction.getUpdatedAt()
        );
    }

    public DocumentExtraction createPendingExtraction(com.mediassist.platform.document.domain.MedicalDocument document) {
        DocumentExtraction extraction = new DocumentExtraction();
        extraction.setDocument(document);
        extraction.setExtractionStatus(ExtractionStatus.PENDING);
        return extraction;
    }
}
