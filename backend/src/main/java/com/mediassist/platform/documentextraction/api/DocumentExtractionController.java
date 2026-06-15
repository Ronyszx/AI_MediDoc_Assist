package com.mediassist.platform.documentextraction.api;

import com.mediassist.platform.documentextraction.api.dto.DocumentExtractionResponse;
import com.mediassist.platform.documentextraction.api.dto.DocumentExtractionTextResponse;
import com.mediassist.platform.documentextraction.application.DocumentExtractionApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/documents")
@Tag(name = "Document Extractions", description = "PDF text extraction endpoints")
public class DocumentExtractionController {

    private static final String ACTOR_HEADER = "X-Actor";

    private final DocumentExtractionApplicationService documentExtractionApplicationService;

    public DocumentExtractionController(DocumentExtractionApplicationService documentExtractionApplicationService) {
        this.documentExtractionApplicationService = documentExtractionApplicationService;
    }

    @PostMapping("/{documentId}/extract")
    @Operation(summary = "Extract text from a PDF", description = "Extracts text from a stored PDF using Apache PDFBox.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document extraction processed successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<DocumentExtractionResponse> extractDocument(
        @PathVariable @NotNull UUID documentId,
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy
    ) {
        return ResponseEntity.ok(documentExtractionApplicationService.extractDocument(documentId, performedBy));
    }

    @GetMapping("/{documentId}/extraction")
    @Operation(summary = "Get extraction status", description = "Returns extraction status and metadata for a document.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Extraction metadata retrieved successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Document or extraction not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<DocumentExtractionResponse> getExtraction(
        @PathVariable @NotNull UUID documentId
    ) {
        return ResponseEntity.ok(documentExtractionApplicationService.getExtraction(documentId));
    }

    @GetMapping("/{documentId}/text")
    @Operation(summary = "Get extracted text", description = "Returns the extracted text for a completed PDF extraction.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Extracted text retrieved successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Document or extraction not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Extraction has not completed yet",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<DocumentExtractionTextResponse> getExtractedText(
        @PathVariable @NotNull UUID documentId
    ) {
        return ResponseEntity.ok(documentExtractionApplicationService.getExtractedText(documentId));
    }
}
