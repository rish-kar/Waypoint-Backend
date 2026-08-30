package com.waypoint.backend;

import com.waypoint.backend.config.admin.AdminProperties;
import com.waypoint.backend.config.ai.OpenAiProperties;
import com.waypoint.backend.config.application.AppProperties;
import com.waypoint.backend.config.application.CorsProperties;
import com.waypoint.backend.config.auth.GoogleOAuthProperties;
import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.config.auth.MicrosoftOAuthProperties;
import com.waypoint.backend.config.auth.WaypointSessionProperties;
import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.security.jwt.JwtProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        AdminProperties.class,
        OpenAiProperties.class,
        AppProperties.class,
        CorsProperties.class,
        GoogleProperties.class,
        GoogleOAuthProperties.class,
        MicrosoftOAuthProperties.class,
        WaypointSessionProperties.class,
        JwtProperties.class,
        LemonSqueezyProperties.class
})
public class WaypointBackendApplication {

    public static void main(String[] args) {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(WaypointBackendApplication.class, args);
    }
}
