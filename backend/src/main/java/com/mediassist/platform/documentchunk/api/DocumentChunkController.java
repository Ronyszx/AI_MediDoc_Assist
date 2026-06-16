package com.mediassist.platform.documentchunk.api;

import com.mediassist.platform.documentchunk.api.dto.DocumentChunkResponse;
import com.mediassist.platform.documentchunk.application.DocumentChunkApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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
@Tag(name = "Document Chunks", description = "Document text chunking endpoints")
public class DocumentChunkController {

    private static final String ACTOR_HEADER = "X-Actor";

    private final DocumentChunkApplicationService documentChunkApplicationService;

    public DocumentChunkController(DocumentChunkApplicationService documentChunkApplicationService) {
        this.documentChunkApplicationService = documentChunkApplicationService;
    }

    @PostMapping("/{documentId}/chunk")
    @Operation(
        summary = "Chunk extracted document text",
        description = "Splits completed extracted text into ordered overlapping chunks."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document chunks generated or retrieved successfully"),
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
            description = "Document extraction has not completed yet",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<List<DocumentChunkResponse>> chunkDocument(
        @PathVariable @NotNull UUID documentId,
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy
    ) {
        return ResponseEntity.ok(documentChunkApplicationService.chunkDocument(documentId, performedBy));
    }

    @GetMapping("/{documentId}/chunks")
    @Operation(
        summary = "List document chunks",
        description = "Returns ordered chunks generated from a document extraction."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document chunks retrieved successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Document or extraction not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<List<DocumentChunkResponse>> listChunksForDocument(
        @PathVariable @NotNull UUID documentId
    ) {
        return ResponseEntity.ok(documentChunkApplicationService.listChunksForDocument(documentId));
    }
}
