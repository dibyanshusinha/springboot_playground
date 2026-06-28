package com.dibyanshusinha.apiserv.exception;

import com.dibyanshusinha.apiserv.generated.model.ErrorResponse;
import com.dibyanshusinha.apiserv.observability.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);


    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return problem(exception.getStatus(), exception.getTitle(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ErrorResponse errorResponse = errorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request fields are invalid.",
                request);

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        errorResponse.setErrors(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameter", exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "Request body is missing or invalid.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Data integrity violation", "Request conflicts with existing data.", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(OptimisticLockingFailureException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", "The resource was modified by another request. Please retry.", request);
    }

    @ExceptionHandler(PreconditionFailedException.class)
    public ResponseEntity<ErrorResponse> handlePreconditionFailed(PreconditionFailedException exception, HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Precondition Failed", exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error occurred on path [{}]: {}", request.getRequestURI(), exception.getMessage(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "An unexpected error occurred.", request);
    }

    private ResponseEntity<ErrorResponse> problem(@NonNull HttpStatus status, String title, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(errorResponse(status, title, detail, request));
    }

    private ErrorResponse errorResponse(@NonNull HttpStatus status, String title, String detail, HttpServletRequest request) {
        return new ErrorResponse(
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                correlationId(request))
                .type(URI.create("https://api.example.com/problems/" + title.toLowerCase().replace(" ", "-")));
    }

    private String correlationId(HttpServletRequest request) {
        Object correlationId = request.getAttribute(CorrelationId.REQUEST_ATTRIBUTE);
        if (correlationId instanceof String value && !value.isBlank()) {
            return value;
        }
        return CorrelationId.resolve(request.getHeader(CorrelationId.HEADER_NAME));
    }
}
