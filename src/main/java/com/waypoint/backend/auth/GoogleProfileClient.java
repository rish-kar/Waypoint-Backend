package com.waypoint.backend.auth;

public interface GoogleProfileClient {
    GoogleProfile fetchProfile(String accessToken);
}
