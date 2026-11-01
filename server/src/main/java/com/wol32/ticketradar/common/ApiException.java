package com.wol32.ticketradar.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** Thrown by services for expected failures (404, 400 ...). Message is always a Korean user-facing sentence. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final transient Map<String, String> fields;

    public ApiException(HttpStatus status, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.fields = fields;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message, null);
    }

    public static ApiException badRequest(String message, Map<String, String> fields) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, fields);
    }

    public HttpStatus status() {
        return status;
    }

    public ApiError toError() {
        return new ApiError(getMessage(), fields);
    }
}
