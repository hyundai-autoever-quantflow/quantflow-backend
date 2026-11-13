package org.example.quantflowbackend.domain.broker.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Distinguishes Toss 401 from other 422 errors, such as an IP restriction. */
public class TossAuthenticationException extends ResponseStatusException {
    public TossAuthenticationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
