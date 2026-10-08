package com.limoz.fleet.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error payload returned by every endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Standard error response")
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        @Schema(description = "Machine readable error code where available") String code,
        @Schema(description = "Field level validation errors") List<FieldError> errors) {

    public record FieldError(String field, Object rejectedValue, String message) {}

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null, null);
    }
}
