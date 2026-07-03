package com.waypoint.backend;

import com.waypoint.backend.billing.LemonSqueezyProperties;
import com.waypoint.backend.auth.GoogleProperties;
import com.waypoint.backend.config.AppProperties;
import com.waypoint.backend.config.CorsProperties;
import com.waypoint.backend.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AppProperties.class,
        CorsProperties.class,
        GoogleProperties.class,
        JwtProperties.class,
        LemonSqueezyProperties.class
})
public class WaypointBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaypointBackendApplication.class, args);
    }
}
