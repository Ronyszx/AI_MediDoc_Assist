package com.mediassist.platform.patient.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import com.mediassist.platform.patient.api.dto.PatientCreateRequest;
import com.mediassist.platform.patient.api.dto.PatientResponse;
import com.mediassist.platform.patient.api.dto.PatientUpdateRequest;
import com.mediassist.platform.patient.domain.Patient;
import com.mediassist.platform.patient.domain.PatientRepository;
import com.mediassist.platform.patient.domain.PatientStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class PatientApplicationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final AuditApplicationService auditApplicationService;
    private final ObjectMapper objectMapper;

    public PatientApplicationService(
        PatientRepository patientRepository,
        PatientMapper patientMapper,
        AuditApplicationService auditApplicationService,
        ObjectMapper objectMapper
    ) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
        this.auditApplicationService = auditApplicationService;
        this.objectMapper = objectMapper;
    }

    public PatientResponse createPatient(
        @NotNull @Valid PatientCreateRequest request,
        @NotBlank String performedBy
    ) {
        String normalizedMrn = request.mrn().trim();
        if (patientRepository.existsByMrn(normalizedMrn)) {
            throw new DuplicatePatientMrnException(normalizedMrn);
        }

        Patient patient = patientMapper.toNewPatient(request);
        Patient savedPatient = patientRepository.save(patient);

        auditApplicationService.recordAuditEvent(new AuditEventCreateRequest(
            AuditEntityType.PATIENT,
            savedPatient.getId(),
            AuditAction.CREATED,
            performedBy,
            buildPatientCreatedDetails(savedPatient)
        ));

        return patientMapper.toResponse(savedPatient);
    }

    public PatientResponse updatePatient(
        @NotNull UUID patientId,
        @NotNull @Valid PatientUpdateRequest request,
        @NotBlank String performedBy
    ) {
        Patient patient = findPatientById(patientId);
        patientMapper.updatePatient(patient, request);

        Patient savedPatient = patientRepository.save(patient);

        auditApplicationService.recordAuditEvent(new AuditEventCreateRequest(
            AuditEntityType.PATIENT,
            savedPatient.getId(),
            AuditAction.UPDATED,
            performedBy,
            buildPatientUpdatedDetails(savedPatient)
        ));

        return patientMapper.toResponse(savedPatient);
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatientById(@NotNull UUID patientId) {
        return patientMapper.toResponse(findPatientById(patientId));
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> listPatients() {
        return patientRepository.findAll().stream()
            .sorted(patientListComparator())
            .map(patientMapper::toResponse)
            .toList();
    }

    public PatientResponse changePatientStatus(
        @NotNull UUID patientId,
        @NotNull PatientStatus status,
        @NotBlank String performedBy
    ) {
        Patient patient = findPatientById(patientId);
        PatientStatus previousStatus = patient.getStatus();

        if (previousStatus == status) {
            return patientMapper.toResponse(patient);
        }

        patient.setStatus(status);
        Patient savedPatient = patientRepository.save(patient);

        auditApplicationService.recordAuditEvent(new AuditEventCreateRequest(
            AuditEntityType.PATIENT,
            savedPatient.getId(),
            AuditAction.STATUS_CHANGED,
            performedBy,
            buildPatientStatusChangedDetails(savedPatient, previousStatus)
        ));

        return patientMapper.toResponse(savedPatient);
    }

    private Patient findPatientById(UUID patientId) {
        return patientRepository.findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
    }

    private Comparator<Patient> patientListComparator() {
        return Comparator
            .comparing(Patient::getLastName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Patient::getFirstName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Patient::getMrn, String.CASE_INSENSITIVE_ORDER);
    }

    private ObjectNode buildPatientCreatedDetails(Patient patient) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("mrn", patient.getMrn());
        details.put("status", patient.getStatus().name());
        return details;
    }

    private ObjectNode buildPatientUpdatedDetails(Patient patient) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("mrn", patient.getMrn());
        details.put("status", patient.getStatus().name());
        return details;
    }

    private ObjectNode buildPatientStatusChangedDetails(Patient patient, PatientStatus previousStatus) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("mrn", patient.getMrn());
        details.put("fromStatus", previousStatus.name());
        details.put("toStatus", patient.getStatus().name());
        return details;
    }
}
