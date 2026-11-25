package com.epam.rd.autocode.assessment.appliances.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Locale;

/**
 * One place that turns exceptions into a friendly, translated error page.
 *
 * Users only ever see a translated title and message. Technical details (exception text, stack
 * traces) go to the log, never to the page.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    static final String ERROR_VIEW = "error/error";

    private final MessageSource messageSource;

    // ---------- business exceptions ----------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleNotFound(ResourceNotFoundException ex, HttpServletRequest request, Locale locale) {
        log.warn("Not found at {}: {} {}", request.getRequestURI(), ex.getMessageKey(), ex.getArgs());
        return build(HttpStatus.NOT_FOUND, resolve(ex, locale), locale);
    }

    @ExceptionHandler(EmptyOrderException.class)
    public ModelAndView handleEmptyOrder(EmptyOrderException ex, HttpServletRequest request, Locale locale) {
        log.warn("Rejected request at {}: {}", request.getRequestURI(), ex.getMessageKey());
        return build(HttpStatus.BAD_REQUEST, resolve(ex, locale), locale);
    }

    /** Duplicates, "in use", "already approved": the request conflicts with the current data. */
    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusiness(BusinessException ex, HttpServletRequest request, Locale locale) {
        log.warn("Business rule violated at {}: {}", request.getRequestURI(), ex.getMessageKey());
        return build(HttpStatus.CONFLICT, resolve(ex, locale), locale);
    }

    // ---------- technical exceptions ----------

    /** Thrown by @PreAuthorize / method security. (URL rules are handled by the security filter chain.) */
    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Locale locale) {
        log.warn("SECURITY: access denied for user '{}' at {} {}",
                request.getRemoteUser(), request.getMethod(), request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, null, locale);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request, Locale locale) {
        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, null, locale);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ModelAndView handleBadRequest(Exception ex, HttpServletRequest request, Locale locale) {
        log.warn("Bad request at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, null, locale);
    }

    /**
     * Everything else. Spring's own web exceptions (405, 415, missing parameter, ...) implement
     * ErrorResponse and keep their status; anything unexpected becomes a 500 and is logged with its stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleAny(Exception ex, HttpServletRequest request, Locale locale) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            log.warn("Request failed at {} with status {}: {}", request.getRequestURI(),
                    errorResponse.getStatusCode().value(), ex.getMessage());
            return build(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, null, locale);
        }
        log.error("Unexpected error at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, null, locale);
    }

    // ---------- helpers ----------

    /** Translates the exception's message key (and arguments) into the user's language. */
    private String resolve(BusinessException ex, Locale locale) {
        return messageSource.getMessage(ex.getMessageKey(), ex.getArgs(), ex.getMessageKey(), locale);
    }

    /**
     * Builds the error page. A null message means "use the standard message for this status".
     */
    private ModelAndView build(HttpStatus status, String message, Locale locale) {
        String prefix = switch (status) {
            case BAD_REQUEST -> "error.400";
            case FORBIDDEN -> "error.403";
            case NOT_FOUND -> "error.404";
            case CONFLICT -> "error.409";
            case INTERNAL_SERVER_ERROR -> "error.500";
            default -> null;
        };
        String title = prefix == null
                ? text("error.title", locale)
                : text(prefix + ".title", locale);
        String body = message != null ? message
                : prefix == null ? text("error.generic", locale) : text(prefix + ".message", locale);

        ModelAndView mav = new ModelAndView(ERROR_VIEW);
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("title", title);
        mav.addObject("message", body);
        return mav;
    }

    private String text(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
    }
}