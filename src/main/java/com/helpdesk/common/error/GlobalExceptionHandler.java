package com.helpdesk.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException ex, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail unauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), req);
    }

    @ExceptionHandler(ForbiddenActionException.class)
    public ProblemDetail forbidden(ForbiddenActionException ex, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), req);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ProblemDetail tooLarge(PayloadTooLargeException ex, HttpServletRequest req) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", ex.getMessage(), req);
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ProblemDetail unsupported(UnsupportedMediaTypeException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", ex.getMessage(), req);
    }

    @ExceptionHandler(UnprocessableException.class)
    public ProblemDetail unprocessable(UnprocessableException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed",
                ex.getMessage(), req);
        pd.setProperty("violations", Map.of("body", ex.getMessage()));
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed",
                "Request validation failed", req);
        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            violations.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        pd.setProperty("violations", violations);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "Malformed JSON body", req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail uploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large",
                "File exceeds the 10 MB limit", req);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ProblemDetail unsupportedMedia(org.springframework.web.HttpMediaTypeNotSupportedException ex,
                                           HttpServletRequest req) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type",
                "Content type not supported for this endpoint", req);
    }

    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MultipartException.class})
    public ProblemDetail badMultipart(Exception ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Missing or malformed multipart field: " + ex.getMessage(), req);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ProblemDetail missingParam(org.springframework.web.bind.MissingServletRequestParameterException ex,
                                      HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Missing required parameter: " + ex.getParameterName(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail noResource(NoResourceFoundException ex, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "Resource not found", req);
    }
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail auth(AuthenticationException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denied(AccessDeniedException ex, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "Insufficient permissions", req);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ProblemDetail generic(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Unexpected error", req);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setInstance(java.net.URI.create(req.getRequestURI()));
        return pd;
    }
}
