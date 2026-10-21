package de.financetool.shared.web;

import de.financetool.shared.ProblemCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 {@link ProblemDetail} with a closed {@link ProblemCode} in {@code code}.
 * Unexpected exceptions never expose their message or stack trace.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    public static final String CODE_PROPERTY = "code";

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");
        problem.setProperty(CODE_PROPERTY, ProblemCode.INTERNAL_ERROR);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE_PROPERTY))) {
            problem.setProperty(CODE_PROPERTY, codeFor(statusCode));
        }
        return response;
    }

    static ProblemCode codeFor(HttpStatusCode status) {
        if (status.value() == 404) {
            return ProblemCode.NOT_FOUND;
        }
        if (status.value() == 400 || status.value() == 422) {
            return ProblemCode.VALIDATION_FAILED;
        }
        return status.is4xxClientError() ? ProblemCode.REQUEST_REJECTED : ProblemCode.INTERNAL_ERROR;
    }
}
