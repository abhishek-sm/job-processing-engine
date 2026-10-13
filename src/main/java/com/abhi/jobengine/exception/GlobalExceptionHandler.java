package com.abhi.jobengine.exception;

import com.abhi.jobengine.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns exceptions into a consistent {@link ApiError} body. Unexpected errors are logged and
 * answered with a generic 500 so stack traces and database details never reach the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(JobNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidJobRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidJobRequestException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler({DuplicateIdempotencyKeyException.class, JobStateConflictException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    /** {@code @Valid @RequestBody} failures, including nested payload parameters. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.add(fieldError(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.add(fieldError(error.getObjectName(), error.getDefaultMessage())));
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", request, fieldErrors);
    }

    /** Constraint failures on query/path parameters, such as {@code size > 100}. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleParameterValidation(HandlerMethodValidationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error -> fieldErrors.add(fieldError(
                        result.getMethodParameter().getParameterName(), error.getDefaultMessage()))));
        return build(HttpStatus.BAD_REQUEST, "Request parameter validation failed", request, fieldErrors);
    }

    /** Unparseable path/query values: a malformed UUID, an unknown status or job type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'"
                + allowedValuesSuffix(ex.getRequiredType());
        return build(HttpStatus.BAD_REQUEST, message, request, List.of());
    }

    /** Malformed JSON, or a JSON value of the wrong type (for example an unknown job type). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        String message = "Malformed JSON request body";
        InvalidFormatException invalidFormat = findCause(ex, InvalidFormatException.class);
        JacksonException jacksonException = findCause(ex, JacksonException.class);
        if (invalidFormat != null) {
            message = "Invalid value '" + invalidFormat.getValue() + "' for field '"
                    + fieldPath(invalidFormat) + "'" + allowedValuesSuffix(invalidFormat.getTargetType());
        } else if (jacksonException != null && !jacksonException.getPath().isEmpty()) {
            message = "Invalid value for field '" + fieldPath(jacksonException) + "'";
        }
        return build(HttpStatus.BAD_REQUEST, message, request, List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Database error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred", request, List.of());
    }

    /**
     * Everything else. Spring MVC's own exceptions (unknown path, unsupported method, missing
     * parameter, ...) carry their status; anything unrecognised becomes a generic 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            return build(errorResponse.getStatusCode(), errorResponse.getBody().getDetail(), request, List.of());
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, List.of());
    }

    private static ResponseEntity<ApiError> build(HttpStatusCode status, String message,
                                                  HttpServletRequest request,
                                                  List<ApiError.FieldError> fieldErrors) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(resolved != null ? resolved.getReasonPhrase() : null)
                .message(message)
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private static ApiError.FieldError fieldError(String field, String message) {
        return ApiError.FieldError.builder().field(field).message(message).build();
    }

    private static String allowedValuesSuffix(Class<?> type) {
        if (type == null || !type.isEnum()) {
            return "";
        }
        return "; allowed values: " + Arrays.toString(type.getEnumConstants());
    }

    /** Dotted JSON path such as {@code payload.generateReportParameters.startDate}. */
    private static String fieldPath(JacksonException ex) {
        return ex.getPath().stream()
                .map(reference -> reference.getPropertyName() != null
                        ? reference.getPropertyName()
                        : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }
}
