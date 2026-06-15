package com.mediassist.platform.shared.exception;

import com.mediassist.platform.document.application.DuplicateDocumentStoragePathException;
import com.mediassist.platform.document.application.MedicalDocumentNotFoundException;
import com.mediassist.platform.document.application.storage.DocumentStorageOperationException;
import com.mediassist.platform.document.application.storage.InvalidDocumentFileException;
import com.mediassist.platform.document.application.storage.StoredDocumentNotFoundException;
import com.mediassist.platform.documentextraction.application.DocumentExtractionNotCompletedException;
import com.mediassist.platform.documentextraction.application.DocumentExtractionNotFoundException;
import com.mediassist.platform.documentextraction.application.DocumentExtractionProcessingException;
import com.mediassist.platform.patient.application.DuplicatePatientMrnException;
import com.mediassist.platform.patient.application.PatientNotFoundException;
import com.mediassist.platform.shared.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.context.request.WebRequest;
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

    @ExceptionHandler(InvalidDocumentFileException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidDocumentFile(
        InvalidDocumentFileException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid Document File", exception.getMessage(), request);
    }

    @ExceptionHandler(StoredDocumentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleStoredDocumentNotFound(
        StoredDocumentNotFoundException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Stored Document Not Found", exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentStorageOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentStorageOperation(
        DocumentStorageOperationException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Document Storage Error",
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(DocumentExtractionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentExtractionNotFound(
        DocumentExtractionNotFoundException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Document Extraction Not Found", exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentExtractionNotCompletedException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentExtractionNotCompleted(
        DocumentExtractionNotCompletedException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Document Extraction Not Completed", exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentExtractionProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentExtractionProcessing(
        DocumentExtractionProcessingException exception,
        HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Document Extraction Failed", exception.getMessage(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
        MaxUploadSizeExceededException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest webRequest
    ) {
        return buildFrameworkErrorResponse(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "Payload Too Large",
            "Uploaded file exceeds the configured maximum size",
            webRequest
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest webRequest
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("; "));

        return buildBadRequestResponse("Validation Failed", message, webRequest);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest webRequest
    ) {
        String message = exception.getAllErrors().stream()
            .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : error.toString())
            .collect(Collectors.joining("; "));

        return buildBadRequestResponse("Validation Failed", message, webRequest);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
        MissingServletRequestPartException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest webRequest
    ) {
        return buildBadRequestResponse("Validation Failed", exception.getMessage(), webRequest);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
        MissingServletRequestParameterException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest webRequest
    ) {
        return buildBadRequestResponse("Validation Failed", exception.getMessage(), webRequest);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
        TypeMismatchException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest webRequest
    ) {
        String message = "Invalid request parameter";
        if (exception instanceof MethodArgumentTypeMismatchException methodArgumentException) {
            message = "Invalid value for parameter '" + methodArgumentException.getName() + "'";
        }

        return buildBadRequestResponse("Validation Failed", message, webRequest);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest webRequest
    ) {
        return buildBadRequestResponse("Validation Failed", "Malformed request body", webRequest);
    }

    private String formatFieldError(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "is invalid";
        return fieldError.getField() + " " + defaultMessage;
    }

    private ResponseEntity<Object> buildBadRequestResponse(
        String error,
        String message,
        WebRequest webRequest
    ) {
        return buildFrameworkErrorResponse(HttpStatus.BAD_REQUEST, error, message, webRequest);
    }

    private ResponseEntity<Object> buildFrameworkErrorResponse(
        HttpStatus status,
        String error,
        String message,
        WebRequest webRequest
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.resolveReference(WebRequest.REFERENCE_REQUEST);
        ApiErrorResponse body = new ApiErrorResponse(
            LocalDateTime.now(ZoneOffset.UTC),
            status.value(),
            error,
            message,
            request != null ? request.getRequestURI() : ""
        );

        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
        HttpStatus status,
        String error,
        String message,
        HttpServletRequest request
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
            LocalDateTime.now(ZoneOffset.UTC),
            status.value(),
            error,
            message,
            request.getRequestURI()
        );

        return ResponseEntity.status(status).body(body);
    }
}
