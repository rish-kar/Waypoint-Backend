package com.waypoint.backend.utilities.exception;

import org.springframework.http.HttpStatus;

public class AiUnavailableException extends ApiException {
    public AiUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", message);
    }
}
