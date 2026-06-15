package com.mediassist.platform.documentextraction.application;

import java.nio.file.Path;

public interface PdfTextExtractionService {

    ExtractedPdfContent extract(Path pdfPath);
}
