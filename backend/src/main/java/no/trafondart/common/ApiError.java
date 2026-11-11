package no.trafondart.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Error body returned by every failing endpoint.
 *
 * @param code        stable error code, translated by the client
 * @param status      HTTP status code
 * @param fieldErrors per-field validation codes (REQUIRED, LENGTH, FORMAT, RANGE, INVALID)
 * @param details     optional machine-readable extra data (e.g. retryAfterSeconds, the current match state)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, int status, List<FieldError> fieldErrors, Object details) {

    public record FieldError(String field, String code) {
    }

    public static ApiError of(ErrorCode code) {
        return new ApiError(code.name(), code.status().value(), List.of(), null);
    }
}
