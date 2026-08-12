package com.waypoint.backend.utilities.client.google;

import com.waypoint.backend.model.auth.GoogleProfile;

public interface GoogleProfileClient {
    GoogleProfile fetchProfile(String accessToken);
}
