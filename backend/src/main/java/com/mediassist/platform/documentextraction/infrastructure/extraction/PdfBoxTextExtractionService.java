package com.mediassist.platform.documentextraction.infrastructure.extraction;

import com.mediassist.platform.documentextraction.application.ExtractedPdfContent;
import com.mediassist.platform.documentextraction.application.PdfTextExtractionService;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class PdfBoxTextExtractionService implements PdfTextExtractionService {

    private final PdfTextExtractionProperties properties;

    public PdfBoxTextExtractionService(PdfTextExtractionProperties properties) {
        this.properties = properties;
    }

    @Override
    public ExtractedPdfContent extract(Path pdfPath) {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper pdfTextStripper = new PDFTextStripper();
            // Retain detected paragraph and page breaks for downstream chunking.
            pdfTextStripper.setDropThreshold(properties.getParagraphDropThreshold());
            pdfTextStripper.setParagraphEnd("\n\n");
            pdfTextStripper.setPageEnd("\n\n");
            String extractedText = pdfTextStripper.getText(document);
            return new ExtractedPdfContent(extractedText, document.getNumberOfPages());
        } catch (IOException exception) {
            throw new PdfTextExtractionException("Unable to extract text from PDF: " + pdfPath.getFileName(), exception);
        }
    }
}
