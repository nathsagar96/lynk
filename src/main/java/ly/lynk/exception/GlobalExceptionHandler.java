package ly.lynk.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps application and framework exceptions to RFC 9457
 * {@code application/problem+json} responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String PROBLEM_BASE = "https://lynk.ly/problems/";

    @ExceptionHandler(AliasNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(AliasNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "alias-not-found", ex.getMessage(), "The requested alias does not exist");
    }

    @ExceptionHandler(AliasExpiredException.class)
    public ResponseEntity<ProblemDetail> handleExpired(AliasExpiredException ex) {
        return problem(HttpStatus.GONE, "alias-expired", ex.getMessage(), "The requested alias has expired");
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleAlreadyExists(AliasAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, "alias-already-exists", ex.getMessage(), "The requested alias is taken");
    }

    @ExceptionHandler(ReservedAliasException.class)
    public ResponseEntity<ProblemDetail> handleReserved(ReservedAliasException ex) {
        return problem(HttpStatus.BAD_REQUEST, "alias-reserved", ex.getMessage(), "The requested alias is reserved");
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ProblemDetail> handleInvalidUrl(InvalidUrlException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-url", ex.getMessage(), "The supplied URL is not acceptable");
    }

    @ExceptionHandler(AliasGenerationException.class)
    public ResponseEntity<ProblemDetail> handleGenerationFailure(AliasGenerationException ex) {
        log.error("Alias generation failed", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "alias-generation-failed",
                ex.getMessage(),
                "A unique alias could not be generated");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-server-error",
                "An unexpected error occurred",
                "Internal server error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ResponseEntity<Object> response = super.handleMethodArgumentNotValid(ex, headers, status, request);
        if (response.getBody() instanceof ProblemDetail detail) {
            Map<String, String> errors = new LinkedHashMap<>();
            for (FieldError error : ex.getBindingResult().getFieldErrors()) {
                errors.putIfAbsent(error.getField(), error.getDefaultMessage());
            }
            detail.setProperty("errors", errors);
        }
        return response;
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String detail, String title) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(PROBLEM_BASE + type));
        problem.setTitle(title);
        problem.setDetail(detail);
        return ResponseEntity.status(status).body(problem);
    }
}
