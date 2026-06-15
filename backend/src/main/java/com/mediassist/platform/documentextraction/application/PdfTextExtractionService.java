package com.mediassist.platform.documentextraction.application;

import java.nio.file.Path;

public interface PdfTextExtractionService {

    String extractText(Path pdfPath);

    int getPageCount(Path pdfPath);
}