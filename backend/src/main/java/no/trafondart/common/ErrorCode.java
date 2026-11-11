package no.trafondart.common;

import org.springframework.http.HttpStatus;

/**
 * Stable, language-neutral error codes. The frontend translates these (see frontend/src/i18n/locales/*.json,
 * key {@code errors.<CODE>}), so the API never returns user-facing prose.
 */
public enum ErrorCode {
    // generic
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CONFLICT(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    CSRF_INVALID(HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // auth
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    AUTH_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST),
    USERNAME_TAKEN(HttpStatus.CONFLICT),

    // invites
    INVITE_INVALID(HttpStatus.BAD_REQUEST),
    INVITE_EXPIRED(HttpStatus.BAD_REQUEST),
    INVITE_USED_UP(HttpStatus.BAD_REQUEST),
    INVITE_REVOKED(HttpStatus.BAD_REQUEST),

    // users / admin
    LAST_ADMIN(HttpStatus.CONFLICT),
    CANNOT_CHANGE_SELF(HttpStatus.CONFLICT),

    // matches
    MATCH_PLAYERS_SAME(HttpStatus.BAD_REQUEST),
    MATCH_NOT_IN_PROGRESS(HttpStatus.CONFLICT),
    MATCH_OUT_OF_SYNC(HttpStatus.CONFLICT),
    MATCH_NOTHING_TO_UNDO(HttpStatus.CONFLICT),
    MATCH_NOT_SCORER(HttpStatus.FORBIDDEN),
    DISPUTE_NOT_ALLOWED(HttpStatus.CONFLICT),
    DISPUTE_ALREADY_OPEN(HttpStatus.CONFLICT),
    DISPUTE_TOURNAMENT_LOCKED(HttpStatus.CONFLICT),

    // tournaments
    TOURNAMENT_NOT_DRAFT(HttpStatus.CONFLICT),
    TOURNAMENT_NOT_IN_PROGRESS(HttpStatus.CONFLICT),
    TOURNAMENT_TOO_FEW_PLAYERS(HttpStatus.BAD_REQUEST),
    TOURNAMENT_TOO_MANY_PLAYERS(HttpStatus.BAD_REQUEST),
    FIXTURE_NOT_READY(HttpStatus.CONFLICT),

    // social
    KUDOS_OWN_MATCH(HttpStatus.BAD_REQUEST),

    // password reset
    RESET_LINK_INVALID(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
