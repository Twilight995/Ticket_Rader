package com.wol32.ticketradar.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import tools.jackson.databind.ObjectMapper;

import com.wol32.ticketradar.common.ApiResponse;

/**
 * The single place that checks who is calling (ADR 4): HTTP Basic "admin:&lt;ADMIN_PASSWORD&gt;".
 * Registered on "/api/admin/**" by AdminWebConfig, so it uses the same path matching as the controllers
 * (a servlet filter on the raw URI could be bypassed with "/api/admin;x=1/event").
 * Compares SHA-256 digests with MessageDigest.isEqual so timing does not depend on the password.
 * When ADMIN_PASSWORD is empty the admin API stays closed.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    static final String ADMIN_USER = "admin";
    static final String CHALLENGE = "Basic realm=\"ticket-radar-admin\", charset=\"UTF-8\"";
    static final String MESSAGE = "관리자 비밀번호가 필요해요";

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);
    private static final String SCHEME = "Basic ";

    private final byte[] expectedDigest; // null when no password is configured
    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(@Value("${ADMIN_PASSWORD:}") String adminPassword, ObjectMapper objectMapper) {
        boolean configured = adminPassword != null && !adminPassword.isBlank();
        this.expectedDigest = configured ? sha256(adminPassword) : null;
        this.objectMapper = objectMapper;
        if (!configured) {
            log.warn("ADMIN_PASSWORD is empty: the admin API stays closed until it is set in .env");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (isAuthorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(MESSAGE));
        return false;
    }

    private boolean isAuthorized(String header) {
        if (expectedDigest == null || header == null || !header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(SCHEME.length()).trim()),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return false;
        }
        boolean userMatches = ADMIN_USER.equals(decoded.substring(0, colon));
        boolean passwordMatches = MessageDigest.isEqual(expectedDigest, sha256(decoded.substring(colon + 1)));
        return userMatches & passwordMatches; // non-short-circuit: both are always computed
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
