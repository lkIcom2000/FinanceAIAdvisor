package de.financetool.shared;

/**
 * Closed set of machine-readable error codes carried in the {@code code} property of every RFC 9457
 * {@code ProblemDetail}. Later stories extend this enum; clients must not rely on any other value.
 */
public enum ProblemCode {
    VALIDATION_FAILED,
    BAD_CREDENTIALS,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    REQUEST_REJECTED,
    /** A chat turn could not be finished (model or audit unavailable); the question stays saved. */
    TURN_FAILED,
    INTERNAL_ERROR
}
