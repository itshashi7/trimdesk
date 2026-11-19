package com.groomdesk.config;

import com.groomdesk.logging.LogText;
import com.groomdesk.logging.Tickets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All API errors as RFC 7807 problem details: {@code {"status", "title", "detail", "ticket", "errors"?}}.
 * ResponseStatusException (400/401/404/409 thrown by services) is handled by the base class.
 *
 * <p>Every error carries the request's ticket number, so a cashier can quote it and the matching
 * log lines can be found. Expected errors (4xx) are logged as one short WARN line; anything
 * unexpected is logged at ERROR with its stack trace, while the client only sees a generic message.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return expected(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage()), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConflict(DataIntegrityViolationException ex, WebRequest request) {
        // The database message can quote the clashing value (e.g. a phone number); mask it.
        log.warn("Data conflict: {}", LogText.safe(rootMessage(ex), 300));
        return expected(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data"),
                request);
    }

    /** Anything not handled above or by the base class: a bug. Log it fully, tell the client little. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error on {}", path(request), ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Quote the ticket number when reporting this.");
        return withTicket(body);
    }

    /** Adds a field -> message map so the UI can show inline validation errors. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Some fields are invalid");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(expected(body, request));
    }

    /** Every error the base class builds (ResponseStatusException, bad JSON, 405...) passes through here. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (statusCode.is5xxServerError()) {
                log.error("Server error {} on {}: {}", statusCode.value(), path(request), problem.getDetail());
                withTicket(problem);
            } else {
                expected(problem, request);
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail expected(ProblemDetail problem, WebRequest request) {
        log.warn("{} on {}: {}", problem.getStatus(), path(request), LogText.safe(problem.getDetail(), 200));
        return withTicket(problem);
    }

    private static ProblemDetail withTicket(ProblemDetail problem) {
        String ticket = Tickets.current();
        if (ticket != null) {
            problem.setProperty("ticket", ticket);
        }
        return problem;
    }

    private static String path(WebRequest request) {
        if (request instanceof ServletWebRequest servlet) {
            return servlet.getRequest().getMethod() + " " + servlet.getRequest().getRequestURI();
        }
        return "?";
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
