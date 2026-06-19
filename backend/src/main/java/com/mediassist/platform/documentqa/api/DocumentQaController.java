package com.mediassist.platform.documentqa.api;

import com.mediassist.platform.documentqa.api.dto.DocumentQuestionRequest;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionResponse;
import com.mediassist.platform.documentqa.application.DocumentQaApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/documents")
@Tag(name = "Document Questions", description = "Grounded document question answering endpoints")
public class DocumentQaController {

    private static final String ACTOR_HEADER = "X-Actor";

    private final DocumentQaApplicationService documentQaApplicationService;

    public DocumentQaController(DocumentQaApplicationService documentQaApplicationService) {
        this.documentQaApplicationService = documentQaApplicationService;
    }

    @PostMapping("/{documentId}/questions")
    @Operation(
        summary = "Ask a question about a document",
        description = "Retrieves relevant chunks, builds a grounded prompt, and asks the configured LLM to answer from context."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Question answered successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid question request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document, embeddings, or relevant context not found",
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
        ),
        @ApiResponse(
            responseCode = "503",
            description = "LLM service unavailable",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<DocumentQuestionResponse> answerQuestion(
        @PathVariable @NotNull UUID documentId,
        @Valid @RequestBody DocumentQuestionRequest request,
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy
    ) {
        return ResponseEntity.ok(documentQaApplicationService.answerQuestion(documentId, request, performedBy));
    }
}
