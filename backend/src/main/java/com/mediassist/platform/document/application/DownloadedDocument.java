package com.mediassist.platform.document.application;

import org.springframework.core.io.Resource;

public record DownloadedDocument(
    String originalFileName,
    String mimeType,
    long contentLength,
    Resource resource
) {
}
