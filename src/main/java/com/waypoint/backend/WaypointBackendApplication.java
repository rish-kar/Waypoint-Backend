package com.waypoint.backend;

import com.waypoint.backend.config.admin.AdminProperties;
import com.waypoint.backend.config.ai.AiProperties;
import com.waypoint.backend.config.application.AppProperties;
import com.waypoint.backend.config.application.CorsProperties;
import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.security.jwt.JwtProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({
        AdminProperties.class,
        AiProperties.class,
        AppProperties.class,
        CorsProperties.class,
        GoogleProperties.class,
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
