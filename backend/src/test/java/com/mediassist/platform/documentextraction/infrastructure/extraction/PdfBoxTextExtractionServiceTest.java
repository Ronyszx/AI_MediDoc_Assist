package com.mediassist.platform.documentextraction.infrastructure.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediassist.platform.documentextraction.application.ExtractedPdfContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfBoxTextExtractionServiceTest {

    private final PdfBoxTextExtractionService pdfBoxTextExtractionService = new PdfBoxTextExtractionService();

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractTextAndPageCountFromPdf() throws IOException {
        Path pdfPath = tempDir.resolve("medical-document.pdf");
        createPdf(pdfPath, "Patient discharge summary");

        ExtractedPdfContent extractedPdfContent = pdfBoxTextExtractionService.extract(pdfPath);

        assertThat(extractedPdfContent.pageCount()).isEqualTo(1);
        assertThat(extractedPdfContent.extractedText()).contains("Patient discharge summary");
    }

    private void createPdf(Path pdfPath, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(text);
                contentStream.endText();
            }

            document.save(Files.newOutputStream(pdfPath));
        }
    }
}
