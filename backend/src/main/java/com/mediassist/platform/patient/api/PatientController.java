package com.mediassist.platform.patient.api;

import com.mediassist.platform.patient.api.dto.PatientCreateRequest;
import com.mediassist.platform.patient.api.dto.PatientResponse;
import com.mediassist.platform.patient.api.dto.PatientStatusUpdateRequest;
import com.mediassist.platform.patient.api.dto.PatientUpdateRequest;
import com.mediassist.platform.patient.application.PatientApplicationService;
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
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@Validated
@RequestMapping("/api/v1/patients")
@Tag(name = "Patients", description = "Patient management endpoints")
public class PatientController {

    private static final String ACTOR_HEADER = "X-Actor";

    private final PatientApplicationService patientApplicationService;

    public PatientController(PatientApplicationService patientApplicationService) {
        this.patientApplicationService = patientApplicationService;
    }

    @PostMapping
    @Operation(summary = "Create a patient", description = "Creates a new patient record.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Patient created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid patient request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Patient MRN already exists",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<PatientResponse> createPatient(
        @Valid @RequestBody PatientCreateRequest request,
        @Parameter(
            description = "Actor responsible for the request",
            example = "clinic-admin"
        )
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy,
        UriComponentsBuilder uriComponentsBuilder
    ) {
        PatientResponse response = patientApplicationService.createPatient(request, performedBy);
        URI location = uriComponentsBuilder
            .path("/api/v1/patients/{patientId}")
            .buildAndExpand(response.id())
            .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @Operation(summary = "List patients", description = "Returns all patients.")
    @ApiResponse(responseCode = "200", description = "Patients retrieved successfully")
    public ResponseEntity<List<PatientResponse>> listPatients() {
        return ResponseEntity.ok(patientApplicationService.listPatients());
    }

    @GetMapping("/{patientId}")
    @Operation(summary = "Get patient by ID", description = "Returns a patient by its identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Patient retrieved successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Patient not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.mediassist.platform.shared.api.ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<PatientResponse> getPatientById(
        @PathVariable @NotNull UUID patientId
    ) {
        return ResponseEntity.ok(patientApplicationService.getPatientById(patientId));
    }

    @PutMapping("/{patientId}")
    @Operation(summary = "Update patient", description = "Updates an existing patient record.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Patient updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid patient update request",
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
        )
    })
    public ResponseEntity<PatientResponse> updatePatient(
        @PathVariable @NotNull UUID patientId,
        @Valid @RequestBody PatientUpdateRequest request,
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy
    ) {
        return ResponseEntity.ok(patientApplicationService.updatePatient(patientId, request, performedBy));
    }

    @PatchMapping("/{patientId}/status")
    @Operation(summary = "Change patient status", description = "Changes the status of an existing patient.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Patient status updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid patient status request",
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
        )
    })
    public ResponseEntity<PatientResponse> changePatientStatus(
        @PathVariable @NotNull UUID patientId,
        @Valid @RequestBody PatientStatusUpdateRequest request,
        @RequestHeader(ACTOR_HEADER) @NotBlank String performedBy
    ) {
        return ResponseEntity.ok(
            patientApplicationService.changePatientStatus(patientId, request.status(), performedBy)
        );
    }
}
