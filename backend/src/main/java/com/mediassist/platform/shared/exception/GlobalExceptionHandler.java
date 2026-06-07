package com.mediassist.platform.shared.exception;

import com.mediassist.platform.document.application.DuplicateDocumentStoragePathException;
import com.mediassist.platform.document.application.MedicalDocumentNotFoundException;
import com.mediassist.platform.patient.application.DuplicatePatientMrnException;
import com.mediassist.platform.patient.application.PatientNotFoundException;
import com.mediassist.platform.shared.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePatientNotFound(
        PatientNotFoundException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Patient Not Found", exception.getMessage(), request);
    }

    @ExceptionHandler(MedicalDocumentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMedicalDocumentNotFound(
        MedicalDocumentNotFoundException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Medical Document Not Found", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicatePatientMrnException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicatePatientMrn(
        DuplicatePatientMrnException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Duplicate Patient MRN", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateDocumentStoragePathException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateDocumentStoragePath(
        DuplicateDocumentStoragePathException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(
            HttpStatus.CONFLICT,
            "Duplicate Document Storage Path",
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception,
        HttpServletRequest request
    ) {
        String message = exception.getAllErrors().stream()
            .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : error.toString())
            .collect(Collectors.joining("; "));

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Failed", message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        String message = "Invalid value for parameter '" + exception.getName() + "'";
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Failed", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Failed", "Malformed request body", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        org.springframework.web.context.request.WebRequest webRequest
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.resolveReference(
            org.springframework.web.context.request.WebRequest.REFERENCE_REQUEST
        );
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("; "));

        ApiErrorResponse body = new ApiErrorResponse(
            OffsetDateTime.now(ZoneOffset.UTC),
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            message,
            request != null ? request.getRequestURI() : ""
        );

        return ResponseEntity.badRequest().body(body);
    }

    private String formatFieldError(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "is invalid";
        return fieldError.getField() + " " + defaultMessage;
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
        HttpStatus status,
        String error,
        String message,
        HttpServletRequest request
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
            OffsetDateTime.now(ZoneOffset.UTC),
            status.value(),
            error,
            message,
            request.getRequestURI()
        );

        return ResponseEntity.status(status).body(body);
    }
}
