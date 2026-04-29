package com.settlr.settlr_api.exception;

import com.settlr.settlr_api.dto.ErrorResponse;
import com.settlr.settlr_api.dto.ErrorResponse.FieldValidationError;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralised exception handler — catches every exception thrown from
 * any @RestController and returns a structured ErrorResponse JSON.
 *
 * Handles:
 *   400  Bean validation, malformed JSON, illegal arguments
 *   401  Bad credentials, expired/invalid JWT
 *   403  Access denied (authenticated but not authorized)
 *   404  Resource not found
 *   409  Email exists, duplicate member, optimistic lock conflict
 *   500  Catch-all for unhandled exceptions
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // 400 — BAD REQUEST
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Bean Validation failures (@Valid).
     * Returns per-field error details so clients can highlight individual fields.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<FieldValidationError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new FieldValidationError(
                        fe.getField(),
                        fe.getRejectedValue(),
                        fe.getDefaultMessage()))
                .toList();

        String summary = fieldErrors.stream()
                .map(FieldValidationError::message)
                .reduce((a, b) -> a + " | " + b)
                .orElse("Validation failed");

        log.warn("[400 BAD REQUEST] Validation failed | path={} | errors={}",
                request.getRequestURI(), summary);

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(400, "Bad Request", summary,
                        request.getRequestURI(), fieldErrors));
    }

    /** Malformed or missing JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("[400 BAD REQUEST] Malformed JSON body | path={} | reason={}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(400, "Bad Request",
                        "Request body is missing or malformed", request.getRequestURI()));
    }

    /** Illegal argument from service layer logic. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArg(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("[400 BAD REQUEST] Illegal argument | path={} | reason={}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 401 — UNAUTHORIZED
    // ═══════════════════════════════════════════════════════════════════════════

    /** Login failures — never reveal whether the email exists. */
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            RuntimeException ex,
            HttpServletRequest request) {

        log.warn("[401 UNAUTHORIZED] Login failed | path={} | reason={}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Unauthorized",
                        "Invalid email or password", request.getRequestURI()));
    }

    /** Expired JWT — specific message so the client knows to re-authenticate. */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponse> handleExpiredJwt(
            ExpiredJwtException ex,
            HttpServletRequest request) {

        log.warn("[401 UNAUTHORIZED] Expired JWT | path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Unauthorized",
                        "Token has expired — please log in again", request.getRequestURI()));
    }

    /** Any other JWT error (malformed, bad signature, etc.). */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtError(
            JwtException ex,
            HttpServletRequest request) {

        log.warn("[401 UNAUTHORIZED] Invalid JWT | path={} | reason={}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Unauthorized",
                        "Invalid or malformed token", request.getRequestURI()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 403 — FORBIDDEN
    // ═══════════════════════════════════════════════════════════════════════════

    /** Authenticated but not authorized (e.g., not a member of the group). */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("[403 FORBIDDEN] Access denied | path={} | reason={}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden", ex.getMessage(), request.getRequestURI()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 404 — NOT FOUND
    // ═══════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("[404 NOT FOUND] Resource missing | path={} | reason={}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 409 — CONFLICT
    // ═══════════════════════════════════════════════════════════════════════════

    /** Duplicate email during registration. */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex,
            HttpServletRequest request) {

        log.warn("[409 CONFLICT] Duplicate email | path={} | reason={}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    /** Duplicate group member. */
    @ExceptionHandler(UserAlreadyMemberException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyMember(
            UserAlreadyMemberException ex,
            HttpServletRequest request) {

        log.warn("[409 CONFLICT] Duplicate member | path={} | reason={}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    /** Optimistic lock conflict on UserBalance — retryable. */
    @ExceptionHandler(BalanceConflictException.class)
    public ResponseEntity<ErrorResponse> handleBalanceConflict(
            BalanceConflictException ex,
            HttpServletRequest request) {

        log.warn("[409 CONFLICT] Balance version conflict — retryable | path={} | reason={}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 500 — INTERNAL SERVER ERROR (catch-all)
    // ═══════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request) {

        log.error("[500 INTERNAL SERVER ERROR] Unhandled exception | path={}",
                request.getRequestURI(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred. Check server logs.", request.getRequestURI()));
    }
}
