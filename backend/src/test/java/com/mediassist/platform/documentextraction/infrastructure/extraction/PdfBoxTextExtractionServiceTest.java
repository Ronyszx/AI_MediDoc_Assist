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

    private final PdfBoxTextExtractionService pdfBoxTextExtractionService =
        new PdfBoxTextExtractionService(new PdfTextExtractionProperties());

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

    @Test
    void shouldPreserveDetectedParagraphSeparators() throws IOException {
        Path pdfPath = tempDir.resolve("paragraphs.pdf");
        String first = "First paragraph records an assessment.";
        String second = "Second paragraph records a later review.";
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(100, 700);
                stream.showText(first);
                stream.newLineAtOffset(0, -45);
                stream.showText(second);
                stream.endText();
            }
            document.save(pdfPath.toFile());
        }

        String text = pdfBoxTextExtractionService.extract(pdfPath).extractedText();

        assertThat(text).contains(first, second);
        assertThat(text.substring(text.indexOf(first) + first.length(), text.indexOf(second)))
            .contains("\n\n");
    }

    @Test
    void shouldPreservePageSeparatorsAndPageCount() throws IOException {
        Path pdfPath = tempDir.resolve("two-pages.pdf");
        String first = "Record A describes an earlier assessment.";
        String second = "Record B describes a later assessment.";
        try (PDDocument document = new PDDocument()) {
            addTextPage(document, first);
            addTextPage(document, second);
            document.save(pdfPath.toFile());
        }

        ExtractedPdfContent content = pdfBoxTextExtractionService.extract(pdfPath);

        assertThat(content.pageCount()).isEqualTo(2);
        assertThat(content.extractedText()).contains(first, second);
        assertThat(content.extractedText().substring(
            content.extractedText().indexOf(first) + first.length(), content.extractedText().indexOf(second)
        )).contains("\n\n");
    }

    @Test
    void shouldRetainSingleLineBreaksWithinAWrappedParagraph() throws IOException {
        Path pdfPath = tempDir.resolve("wrapped-paragraph.pdf");
        String first = "This paragraph continues on the following line";
        String second = "and ends with the same recorded assessment.";
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(100, 700);
                stream.showText(first);
                stream.newLineAtOffset(0, -14);
                stream.showText(second);
                stream.endText();
            }
            document.save(pdfPath.toFile());
        }

        String text = pdfBoxTextExtractionService.extract(pdfPath).extractedText();

        assertThat(text.substring(text.indexOf(first) + first.length(), text.indexOf(second)))
            .isEqualTo("\n");
    }

    @Test
    void shouldDistinguishSmallFontLineWrappingFromAnActualParagraphGap() throws IOException {
        Path pdfPath = tempDir.resolve("small-font-paragraphs.pdf");
        String first = "This record does not";
        String continuation = "establish an additional condition.";
        String next = "A later paragraph describes a review.";
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10.3f);
                stream.newLineAtOffset(100, 700);
                stream.showText(first);
                stream.newLineAtOffset(0, -15);
                stream.showText(continuation);
                stream.newLineAtOffset(0, -25);
                stream.showText(next);
                stream.endText();
            }
            document.save(pdfPath.toFile());
        }

        String text = pdfBoxTextExtractionService.extract(pdfPath).extractedText();

        assertThat(text.substring(text.indexOf(first) + first.length(), text.indexOf(continuation)))
            .isEqualTo("\n");
        assertThat(text.substring(text.indexOf(continuation) + continuation.length(), text.indexOf(next)))
            .contains("\n\n");
    }

    private void addTextPage(PDDocument document, String text) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(100, 700);
            stream.showText(text);
            stream.endText();
        }
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
