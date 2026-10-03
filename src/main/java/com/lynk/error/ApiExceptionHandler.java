package com.lynk.error;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error the API can return, in one place, as RFC 9457 problem documents.
 * <p>
 * The application exceptions carry only a message and no HTTP knowledge: the status, title and
 * problem type for each one are declared here, so the full error contract of the service can be
 * read top to bottom without leaving this file.
 * <p>
 * Extending {@link ResponseEntityExceptionHandler} covers the framework's own exceptions, and
 * Boot registers its equivalent advice only when none is present, so this class takes its place.
 * Requires {@code spring.mvc.problemdetails.enabled=true}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUrlNotFound(UrlNotFoundException ex, WebRequest request) {
        return respond(
                HttpStatus.NOT_FOUND, "Short URL not found", "/problems/url-not-found", ex.getMessage(), request);
    }

    /**
     * 410 Gone rather than 404, because the resource did once exist and the caller holding a stale
     * link deserves to know it will never come back.
     */
    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<ProblemDetail> handleUrlExpired(UrlExpiredException ex, WebRequest request) {
        return respond(HttpStatus.GONE, "Short URL expired", "/problems/url-expired", ex.getMessage(), request);
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleAliasAlreadyExists(AliasAlreadyExistsException ex, WebRequest request) {
        return respond(
                HttpStatus.CONFLICT, "Alias already exists", "/problems/alias-conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(ReservedAliasException.class)
    public ResponseEntity<ProblemDetail> handleReservedAlias(ReservedAliasException ex, WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Reserved alias", "/problems/reserved-alias", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ProblemDetail> handleInvalidUrl(InvalidUrlException ex, WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid request", "/problems/invalid-url", ex.getMessage(), request);
    }

    /**
     * Bean-validation failures get an {@code errors} array, since RFC 9457 defines no standard field
     * for per-field failures and without it a caller only learns that "validation failed".
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        ResponseEntity<Object> response = super.handleMethodArgumentNotValid(ex, headers, status, request);

        ProblemDetail problem = (ProblemDetail) response.getBody();
        problem.setType(URI.create("/problems/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(instanceOf(request));

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage(),
                        "rejectedValue", String.valueOf(error.getRejectedValue())))
                .toList();
        problem.setProperty("errors", errors);

        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    /**
     * Stamps {@code instance} on the problem documents the base class builds.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex,
            Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        ProblemDetail problem = (body instanceof ProblemDetail detail) ? detail : null;
        if (problem == null) {
            problem = ProblemDetail.forStatus(status);
            if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                problem.setDetail("An unexpected error occurred.");
            }
        }
        problem.setInstance(instanceOf(request));

        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    private ResponseEntity<ProblemDetail> respond(
            HttpStatus status, String title, String type, String detail, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(type));
        problem.setInstance(instanceOf(request));
        return ResponseEntity.status(status).body(problem);
    }

    private URI instanceOf(WebRequest request) {
        return (request instanceof ServletWebRequest servletRequest)
                ? URI.create(servletRequest.getRequest().getRequestURI())
                : null;
    }
}
