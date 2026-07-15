package com.moviebooking.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body for every non-2xx response.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        ErrorCode code,
        String message,
        List<String> details,
        String path
) {

    public static ApiErrorResponse of(int status, ErrorCode code, String message, List<String> details, String path) {
        return new ApiErrorResponse(Instant.now(), status, code, message, details == null ? List.of() : details, path);
    }
}
