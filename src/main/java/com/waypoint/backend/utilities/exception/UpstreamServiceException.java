package com.waypoint.backend.utilities.exception;

import org.springframework.http.HttpStatus;

public class UpstreamServiceException extends ApiException {
    public UpstreamServiceException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_PROVIDER_UNAVAILABLE", message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_PROVIDER_UNAVAILABLE", message, cause);
    }
}
