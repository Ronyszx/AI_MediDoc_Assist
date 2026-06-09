package com.mediassist.platform.document.application.storage;

import com.mediassist.platform.document.application.DocumentStorageDetails;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorageService {

    DocumentStorageDetails store(MultipartFile file);

    StoredDocumentResource load(String storagePath);

    void delete(String storagePath);
}
