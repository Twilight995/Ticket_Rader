package com.wol32.ticketradar.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Every failure leaves the server in the same envelope with Korean user-facing text only.
 * Stack traces, paths, SQL and table names stay in the server log.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public static final String INTERNAL_MESSAGE = "잠시 문제가 생겼어요. 다시 시도해 주세요";
    public static final String INVALID_MESSAGE = "입력값을 확인해 주세요";
    public static final String UNREADABLE_MESSAGE = "요청 형식이 올바르지 않아요";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) {
        return ResponseEntity.status(e.status()).body(ApiResponse.fail(e.toError()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> fields.putIfAbsent(err.getField(), err.getDefaultMessage()));
        return badRequest(new ApiError(INVALID_MESSAGE, fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        Map<String, String> fields = jacksonFieldErrors(e);
        ApiError error = fields.isEmpty() ? ApiError.of(UNREADABLE_MESSAGE) : new ApiError(INVALID_MESSAGE, fields);
        return badRequest(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse framework) {
            // Spring's own 404 / 405 / 415 ...: keep the status, say it in Korean, do not fill the log
            HttpStatusCode status = framework.getStatusCode();
            log.debug("Request rejected by the framework with {}", status);
            return ResponseEntity.status(status).body(ApiResponse.fail(messageFor(status)));
        }
        log.error("Unhandled error while serving a request", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(INTERNAL_MESSAGE));
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "요청한 주소를 찾을 수 없어요";
            case 405 -> "허용되지 않는 요청 방식이에요";
            case 415 -> "지원하지 않는 요청 형식이에요";
            case 400 -> UNREADABLE_MESSAGE;
            default -> status.is4xxClientError() ? "요청을 처리할 수 없어요" : INTERNAL_MESSAGE;
        };
    }

    private static ResponseEntity<ApiResponse<Void>> badRequest(ApiError error) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }

    /** Turns a Jackson parse failure into { "priceTiers[1].checkedOn": "날짜는 ..." } when the failing field is known. */
    private static Map<String, String> jacksonFieldErrors(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof DatabindException de && !de.getPath().isEmpty()) {
                return Map.of(fieldPath(de.getPath()), formatMessage(de));
            }
        }
        return Map.of();
    }

    private static String fieldPath(List<JacksonException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference ref : path) {
            if (ref.getPropertyName() != null) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private static String formatMessage(DatabindException e) {
        if (!(e instanceof InvalidFormatException ife) || ife.getTargetType() == null) {
            return "값의 형식이 올바르지 않아요";
        }
        Class<?> target = ife.getTargetType();
        if (target.isEnum()) {
            return enumMessage(target);
        }
        if (target == LocalDate.class) {
            return "날짜는 YYYY-MM-DD 형식으로 넣어 주세요";
        }
        if (target == LocalDateTime.class) {
            return "일시는 YYYY-MM-DDTHH:mm:ss 형식으로 넣어 주세요";
        }
        if (Number.class.isAssignableFrom(target) || target.isPrimitive()) {
            return "숫자만 입력해 주세요";
        }
        return "값의 형식이 올바르지 않아요";
    }

    // Matched by simple name so this common package does not depend on feature enums.
    private static String enumMessage(Class<?> enumType) {
        return switch (enumType.getSimpleName()) {
            case "RuleType" -> "규정 종류 5개 중 하나를 골라 주세요";
            case "DeliveryMethod" -> "전달 방식 5개 중 하나를 골라 주세요";
            case "EventCategory" -> "공연 종류는 CONCERT 또는 SPORTS 중 하나예요";
            default -> "선택지 중 하나를 골라 주세요";
        };
    }
}
