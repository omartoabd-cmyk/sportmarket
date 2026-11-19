package sportmarket.exception;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ProblemDetail> handleInvalidImage(InvalidImageException exception, WebRequest request) {
        return ResponseEntity.badRequest().body(createProblem(HttpStatus.BAD_REQUEST, "Invalid image", exception.getMessage(), request));
    }
    @ExceptionHandler(ImageTooLargeException.class)
    public ResponseEntity<ProblemDetail> handleImageTooLarge(ImageTooLargeException exception, WebRequest request) {
        return ResponseEntity.status(413).body(createProblem(HttpStatus.CONTENT_TOO_LARGE, "Image too large", exception.getMessage(), request));
    }
    @ExceptionHandler(ImageNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleImageNotFound(ImageNotFoundException exception, WebRequest request) {
        return ResponseEntity.status(404).body(createProblem(HttpStatus.NOT_FOUND, "Image not found", exception.getMessage(), request));
    }
    @ExceptionHandler(ImageStorageException.class)
    public ResponseEntity<ProblemDetail> handleImageStorage(ImageStorageException exception, WebRequest request) {
        return ResponseEntity.status(500).body(createProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Image storage unavailable", exception.getMessage(), request));
    }
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception, createProblem(HttpStatus.CONTENT_TOO_LARGE, "Image too large",
                "Image must not exceed 5 MB; the complete multipart request must not exceed 6 MB.", request), headers,
                HttpStatus.CONTENT_TOO_LARGE, request);
    }

    @ExceptionHandler(InvalidListingFilterException.class)
    public ResponseEntity<ProblemDetail> handleInvalidListingFilter(InvalidListingFilterException exception, WebRequest request) {
        return ResponseEntity.badRequest().body(createProblem(HttpStatus.BAD_REQUEST,
                "Invalid listing filters", exception.getMessage(), request));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleCategoryNotFound(CategoryNotFoundException exception, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createProblem(HttpStatus.NOT_FOUND,
                "Category not found", exception.getMessage(), request));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException exception, WebRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(createProblem(HttpStatus.FORBIDDEN,
                "Forbidden", "You do not have permission to perform this action.", request));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEmail(DuplicateEmailException exception, WebRequest request) {
        ProblemDetail problem = createProblem(HttpStatus.CONFLICT, "Email already registered",
                exception.getMessage(), request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception, WebRequest request) {
        ProblemDetail problem = createProblem(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Invalid email or password.", request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = createProblem(HttpStatus.BAD_REQUEST, "Bad Request",
                "Request validation failed.", request);
        // Return field names and messages only, never rejected passwords or input values.
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message",
                        Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")))
                .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(ListingNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleListingNotFound(ListingNotFoundException exception, WebRequest request) {
        ProblemDetail problem = createProblem(HttpStatus.NOT_FOUND, "Listing not found",
                exception.getMessage(), request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private ProblemDetail createProblem(HttpStatus status, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(((ServletWebRequest) request).getRequest().getRequestURI()));
        return problem;
    }
}
