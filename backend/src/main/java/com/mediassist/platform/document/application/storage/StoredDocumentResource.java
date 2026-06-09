package com.mediassist.platform.document.application.storage;

import org.springframework.core.io.Resource;

public record StoredDocumentResource(
    Resource resource,
    long contentLength
) {
}
