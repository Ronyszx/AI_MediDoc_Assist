package com.mediassist.platform.document.api;

import com.mediassist.platform.document.api.dto.CreateMedicalDocumentRequest;
import com.mediassist.platform.document.api.dto.DocumentStatusUpdateRequest;
import com.mediassist.platform.document.api.dto.MedicalDocumentResponse;
import com.mediassist.platform.document.application.DownloadedDocument;
import com.mediassist.platform.document.application.DocumentApplicationService;
import com.mediassist.platform.document.domain.DocumentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Documents", description = "Medical document metadata and storage endpoints")
public class DocumentController {

    private static final String ACTOR_HEADER = "X-Actor";

    private final DocumentApplicationService documentApplicationService;
    private final DocumentRequestMapper documentRequestMapper;

    public DocumentController(
        DocumentApplicationService documentApplicationService,
        DocumentRequestMapper documentRequestMapper
    ) {
        this.documentApplicationService = documentApplicationService;
        this.documentRequestMapper = documentRequestMapper;
    }

    @PostMapping("/patients/{patientId}/documents")
    @Operation(
        summary = "Create document metadata",
        description = "Creates medical document metadata for a patient without handling binary upload."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Document metadata created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid document request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Patient not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Document storage path already exists",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<MedicalDocumentResponse> createDocumentMetadata(
        @PathVariable @NotNull UUID patientId,
        @Valid @RequestBody CreateMedicalDocumentRequest request,
        @Parameter(
            description = "Actor responsible for the request",
            example = "clinic-admin"
        )
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy,
        UriComponentsBuilder uriComponentsBuilder
    ) {
        MedicalDocumentResponse response = documentApplicationService.createDocumentMetadata(
            patientId,
            documentRequestMapper.toUploadRequest(request),
            documentRequestMapper.toStorageDetails(request),
            performedBy
        );
        URI location = uriComponentsBuilder
            .path("/api/v1/documents/{documentId}")
            .buildAndExpand(response.id())
            .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PostMapping(
        value = "/patients/{patientId}/documents/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(
        summary = "Upload a PDF document",
        description = "Stores a PDF file on the local filesystem and creates document metadata."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Document uploaded successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid upload request or unsupported PDF file",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Patient not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Document storage path already exists",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<MedicalDocumentResponse> uploadDocument(
        @PathVariable @NotNull UUID patientId,
        @Parameter(description = "PDF file to upload", required = true)
        @RequestPart("file") MultipartFile file,
        @Parameter(description = "Document type", required = true)
        @RequestParam("documentType") @NotNull DocumentType documentType,
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy,
        UriComponentsBuilder uriComponentsBuilder
    ) {
        MedicalDocumentResponse response = documentApplicationService.uploadDocument(
            patientId,
            documentType,
            file,
            performedBy
        );
        URI location = uriComponentsBuilder
            .path("/api/v1/documents/{documentId}")
            .buildAndExpand(response.id())
            .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/patients/{patientId}/documents")
    @Operation(summary = "List documents for a patient", description = "Returns all documents for a patient.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Documents retrieved successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Patient not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<List<MedicalDocumentResponse>> listDocumentsForPatient(
        @PathVariable @NotNull UUID patientId
    ) {
        return ResponseEntity.ok(documentApplicationService.listDocumentsForPatient(patientId));
    }

    @GetMapping("/documents/{documentId}")
    @Operation(summary = "Get document by ID", description = "Returns medical document metadata by identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document retrieved successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<MedicalDocumentResponse> getDocumentById(
        @PathVariable @NotNull UUID documentId
    ) {
        return ResponseEntity.ok(documentApplicationService.getDocumentById(documentId));
    }

    @GetMapping(value = "/documents/{documentId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a PDF document", description = "Downloads a stored PDF file by document identifier.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Document downloaded successfully",
            content = @Content(mediaType = "application/pdf")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document metadata or stored file not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<Resource> downloadDocument(
        @PathVariable @NotNull UUID documentId
    ) {
        DownloadedDocument downloadedDocument = documentApplicationService.downloadDocument(documentId);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(downloadedDocument.mimeType()))
            .contentLength(downloadedDocument.contentLength())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(downloadedDocument.originalFileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(downloadedDocument.resource());
    }

    @PatchMapping("/documents/{documentId}/status")
    @Operation(summary = "Change document status", description = "Changes the status of a medical document.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document status updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid document status request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<MedicalDocumentResponse> changeDocumentStatus(
        @PathVariable @NotNull UUID documentId,
        @Valid @RequestBody DocumentStatusUpdateRequest request,
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy
    ) {
        return ResponseEntity.ok(
            documentApplicationService.changeDocumentStatus(documentId, request.status(), performedBy)
        );
    }
}
