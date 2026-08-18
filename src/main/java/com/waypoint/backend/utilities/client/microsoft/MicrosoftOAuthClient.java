package com.waypoint.backend.utilities.client.microsoft;

import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.auth.MicrosoftTokenSet;

public interface MicrosoftOAuthClient {
    MicrosoftTokenSet exchangeAuthorizationCode(String authorizationCode, String codeVerifier);
    MicrosoftProfile fetchProfile(String accessToken);
}
