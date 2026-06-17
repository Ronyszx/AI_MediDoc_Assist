package com.mediassist.platform.documentembedding.api;

import com.mediassist.platform.documentembedding.api.dto.DocumentChunkEmbeddingMetadataResponse;
import com.mediassist.platform.documentembedding.api.dto.DocumentEmbeddingSummaryResponse;
import com.mediassist.platform.documentembedding.api.dto.SemanticSearchRequest;
import com.mediassist.platform.documentembedding.api.dto.SemanticSearchResponse;
import com.mediassist.platform.documentembedding.application.DocumentEmbeddingApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/documents")
@Tag(name = "Document Embeddings", description = "Document chunk embedding and semantic search endpoints")
public class DocumentEmbeddingController {

    private static final String ACTOR_HEADER = "X-Actor";

    private final DocumentEmbeddingApplicationService documentEmbeddingApplicationService;

    public DocumentEmbeddingController(DocumentEmbeddingApplicationService documentEmbeddingApplicationService) {
        this.documentEmbeddingApplicationService = documentEmbeddingApplicationService;
    }

    @PostMapping("/{documentId}/embeddings")
    @Operation(
        summary = "Generate document chunk embeddings",
        description = "Generates embeddings for document chunks that do not already have embeddings for the configured model."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Embeddings generated or skipped successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Document chunks are not available yet",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<DocumentEmbeddingSummaryResponse> generateEmbeddings(
        @PathVariable @NotNull UUID documentId,
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy
    ) {
        return ResponseEntity.ok(documentEmbeddingApplicationService.generateEmbeddings(documentId, performedBy));
    }

    @GetMapping("/{documentId}/embeddings")
    @Operation(
        summary = "Get document embedding metadata",
        description = "Returns embedding metadata for a document without exposing vector values."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Embedding metadata retrieved successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Document or embeddings not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<List<DocumentChunkEmbeddingMetadataResponse>> getEmbeddingMetadata(
        @PathVariable @NotNull UUID documentId
    ) {
        return ResponseEntity.ok(documentEmbeddingApplicationService.getEmbeddingMetadata(documentId));
    }

    @PostMapping("/{documentId}/semantic-search")
    @Operation(
        summary = "Search document chunks semantically",
        description = "Embeds the query and searches stored document chunk embeddings with pgvector cosine similarity."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Semantic search completed successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid search request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document or embeddings not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Document chunks are not available yet",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<SemanticSearchResponse> semanticSearch(
        @PathVariable @NotNull UUID documentId,
        @Valid @RequestBody SemanticSearchRequest request,
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy
    ) {
        return ResponseEntity.ok(documentEmbeddingApplicationService.semanticSearch(documentId, request, performedBy));
    }
}
