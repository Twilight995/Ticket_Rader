package com.wol32.ticketradar.common;

import java.util.Map;

/**
 * Error part of the envelope. message: Korean user-facing sentence. fields: per-field reasons keyed by the
 * request JSON path (e.g. "priceTiers[1].sourceUrl"), or null when not applicable.
 */
public record ApiError(String message, Map<String, String> fields) {

    public static ApiError of(String message) {
        return new ApiError(message, null);
    }
}
