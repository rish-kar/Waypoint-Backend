package com.waypoint.backend.config.metrics;

import com.waypoint.backend.security.metrics.MetricsTokenFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;

@Configuration
public class MetricsSecurityConfig {
    private static final int MIN_PRODUCTION_TOKEN_LENGTH = 32;

    @Bean
    @Order(0)
    SecurityFilterChain metricsSecurityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            Environment environment
    ) throws Exception {
        String metricsToken = validatedMetricsToken(environment);

        http
                .securityMatcher("/actuator/prometheus")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(
                        new MetricsTokenFilter(metricsToken, objectMapper),
                        UsernamePasswordAuthenticationFilter.class
                );

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            http.requiresChannel(channels -> channels.anyRequest().requiresSecure());
        }
        return http.build();
    }

    private String validatedMetricsToken(Environment environment) {
        String token = environment.getProperty("monitoring.metrics-token");
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("MONITORING_METRICS_TOKEN must be configured");
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            String normalized = token.toLowerCase(Locale.ROOT);
            boolean placeholder = normalized.startsWith("local-")
                    || normalized.startsWith("test-")
                    || normalized.contains("placeholder")
                    || normalized.contains("replace")
                    || normalized.contains("change-before-production")
                    || normalized.contains("${");
            if (token.length() < MIN_PRODUCTION_TOKEN_LENGTH || placeholder) {
                throw new IllegalStateException(
                        "MONITORING_METRICS_TOKEN must be a non-placeholder value of at least 32 characters in production"
                );
            }
        }
        return token;
    }
}
