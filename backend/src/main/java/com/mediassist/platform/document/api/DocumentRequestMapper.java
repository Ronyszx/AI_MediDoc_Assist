package com.mediassist.platform.document.api;

import com.mediassist.platform.document.api.dto.CreateMedicalDocumentRequest;
import com.mediassist.platform.document.api.dto.MedicalDocumentUploadRequest;
import com.mediassist.platform.document.application.DocumentStorageDetails;
import org.springframework.stereotype.Component;

@Component
public class DocumentRequestMapper {

    public MedicalDocumentUploadRequest toUploadRequest(CreateMedicalDocumentRequest request) {
        return new MedicalDocumentUploadRequest(
            request.documentType(),
            request.documentDate(),
            request.description()
        );
    }

    public DocumentStorageDetails toStorageDetails(CreateMedicalDocumentRequest request) {
        return new DocumentStorageDetails(
            request.originalFileName(),
            request.storedFileName(),
            request.mimeType(),
            request.fileSizeBytes(),
            request.checksumSha256(),
            request.storagePath()
        );
    }
}
