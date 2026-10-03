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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error the API can return, in one place, as RFC 9457 problem documents.
 *
 * <p>Requires {@code spring.mvc.problemdetails.enabled=true}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(LinkException.class)
    public ResponseEntity<ProblemDetail> handleLink(LinkException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setTitle(ex.title());
        problem.setType(URI.create(ex.type()));
        problem.setInstance(instanceOf(request));
        return ResponseEntity.status(ex.status()).body(problem);
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

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage(),
                        "rejectedValue", String.valueOf(error.getRejectedValue())))
                .toList();

        return ResponseEntity.status(status)
                .headers(headers)
                .body(validationProblem((ProblemDetail) response.getBody(), errors, request));
    }

    /**
     * Bean validation on a query parameter raises a different exception from a bad request body, but
     * it gets the same document. One {@code type} for validation is worth more than letting the
     * shape of the request decide which problem type the client has to recognise.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", result.getMethodParameter().getParameterName(),
                                "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage(),
                                "rejectedValue", String.valueOf(result.getArgument()))))
                .toList();

        return ResponseEntity.status(status)
                .headers(headers)
                .body(validationProblem(ProblemDetail.forStatus(status), errors, request));
    }

    private ProblemDetail validationProblem(
            ProblemDetail problem, List<Map<String, String>> errors, WebRequest request) {
        problem.setType(URI.create("/problems/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(instanceOf(request));
        problem.setProperty("errors", errors);
        return problem;
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

    private URI instanceOf(WebRequest request) {
        return (request instanceof ServletWebRequest servletRequest)
                ? URI.create(servletRequest.getRequest().getRequestURI())
                : null;
    }
}
