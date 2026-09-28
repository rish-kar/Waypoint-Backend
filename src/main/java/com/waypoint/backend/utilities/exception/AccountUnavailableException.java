package com.waypoint.backend.utilities.exception;

import org.springframework.http.HttpStatus;

public class AccountUnavailableException extends ApiException {
    public AccountUnavailableException() {
        super(HttpStatus.UNAUTHORIZED, "ACCOUNT_DELETED", "Waypoint account no longer exists");
    }
}
