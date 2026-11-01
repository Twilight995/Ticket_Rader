package com.wol32.ticketradar.common;

/**
 * The single response shape of every API: { ok, data, error }.
 * error is a Korean user-facing message or null. Never put stack traces, paths or table names here.
 */
public record ApiResponse<T>(boolean ok, T data, String error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String error) {
        return new ApiResponse<>(false, null, error);
    }
}
