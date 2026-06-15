package com.mediassist.platform.documentextraction.application;

public record ExtractedPdfContent(
    String extractedText,
    int pageCount
) {
}
