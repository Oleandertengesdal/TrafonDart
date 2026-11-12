package no.trafondart.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorReporter errorReporter;

    public GlobalExceptionHandler(org.springframework.beans.factory.ObjectProvider<ErrorReporter> errorReporter) {
        this.errorReporter = errorReporter.getIfAvailable();
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        ErrorCode code = ex.code();
        var builder = ResponseEntity.status(code.status());
        if (code == ErrorCode.RATE_LIMITED && ex.details() instanceof RateLimiter.Limited limited) {
            builder.header("Retry-After", String.valueOf(limited.retryAfterSeconds()));
        }
        return builder.body(new ApiError(code.name(), code.status().value(), List.of(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), mapConstraint(fe.getCode())))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(ErrorCode.VALIDATION_FAILED.name(), 400, fields, null));
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadInput(Exception ex) {
        log.debug("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiError.of(ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(409).body(ApiError.of(ErrorCode.CONFLICT));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ApiError.of(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler({NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception ex) {
        return ResponseEntity.status(404).body(ApiError.of(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(Exception ex) {
        return ResponseEntity.status(415).body(new ApiError("UNSUPPORTED_MEDIA_TYPE", 415, List.of(), null));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleNotAcceptable(Exception ex) {
        return ResponseEntity.status(406).body(new ApiError("NOT_ACCEPTABLE", 406, List.of(), null));
    }

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.MissingPathVariableException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> handleMissing(Exception ex) {
        return ResponseEntity.badRequest().body(ApiError.of(ErrorCode.VALIDATION_FAILED));
    }

    /** A unique or foreign-key constraint decided a race between two requests. Never a 500. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.info("Constraint violation answered with 409 CONFLICT");
        log.debug("Constraint violation", ex);
        return ResponseEntity.status(409).body(ApiError.of(ErrorCode.CONFLICT));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405).body(new ApiError("METHOD_NOT_ALLOWED", 405, List.of(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        // Log the details server-side only; never leak stack traces or SQL to the client.
        log.error("Unhandled exception", ex);
        if (errorReporter != null) errorReporter.report(ex, request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(500).body(ApiError.of(ErrorCode.INTERNAL_ERROR));
    }

    private static String mapConstraint(String constraint) {
        if (constraint == null) return "INVALID";
        return switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Size", "Length" -> "LENGTH";
            case "Pattern", "Email" -> "FORMAT";
            case "Min", "Max", "Positive", "PositiveOrZero", "Range", "Future" -> "RANGE";
            default -> "INVALID";
        };
    }
}
