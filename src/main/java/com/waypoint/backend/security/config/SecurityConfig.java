package com.waypoint.backend.security.config;

import com.waypoint.backend.config.admin.AdminProperties;
import com.waypoint.backend.config.application.CorsProperties;
import com.waypoint.backend.model.common.ApiErrorResponse;
import com.waypoint.backend.security.admin.AdminTotpFilter;
import com.waypoint.backend.security.admin.AdminTotpVerifier;
import com.waypoint.backend.security.jwt.JwtAuthenticationFilter;
import com.waypoint.backend.security.ratelimit.DistributedRateLimiter;
import com.waypoint.backend.security.ratelimit.RequestRateLimitFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder adminPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUserDetailsService(
            AdminProperties adminProperties,
            PasswordEncoder adminPasswordEncoder
    ) {
        UserDetails admin = User.withUsername(adminProperties.id())
                .password(adminPasswordEncoder.encode(adminProperties.password()))
                .authorities(Collections.emptyList())
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            UserDetailsService adminUserDetailsService,
            AdminProperties adminProperties,
            AdminTotpVerifier adminTotpVerifier,
            DistributedRateLimiter distributedRateLimiter,
            Environment environment
    ) throws Exception {
        http
                .securityMatcher("/api/v1/admin/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers(request ->
                        environment.acceptsProfiles(Profiles.of("dev", "test"))
                                || request.getHeader("X-Admin-TOTP") != null))
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(adminUserDetailsService)
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, authException) ->
                        writeSecurityError(
                                objectMapper,
                                request,
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Invalid admin credentials"
                        )))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> writeSecurityError(
                                objectMapper,
                                request,
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Invalid admin credentials"
                        ))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeSecurityError(
                                objectMapper,
                                request,
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN",
                                "Admin access denied"
                        )))
                .addFilterBefore(new RequestRateLimitFilter(distributedRateLimiter), BasicAuthenticationFilter.class);

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            http.requiresChannel(channels -> channels.anyRequest().requiresSecure());
            http.addFilterAfter(new AdminTotpFilter(adminProperties, adminTotpVerifier), BasicAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DistributedRateLimiter distributedRateLimiter,
            ObjectMapper objectMapper,
            Environment environment
    ) throws Exception {
        http
                // Stateless bearer-token API; authentication is never supplied by cookies or an HTTP session.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/google").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/google/start", "/api/v1/auth/google/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/microsoft/start").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/microsoft/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/session/exchange").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/session/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/models").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ai/intent", "/api/v1/ai/chat").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/lemonsqueezy").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> writeSecurityError(
                                objectMapper,
                                request,
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Authentication required"
                        ))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeSecurityError(
                                objectMapper,
                                request,
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN",
                                "Access denied"
                        )))
                .addFilterBefore(new RequestRateLimitFilter(distributedRateLimiter), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            http.requiresChannel(channels -> channels.anyRequest().requiresSecure());
        }
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties, Environment environment) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            // Temporary test mode: Cloud AI is intentionally callable from any local/unpacked frontend origin.
            // Production remains restricted to configured origins.
            configuration.setAllowedOriginPatterns(List.of("*"));
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Signature", "X-Request-ID", "X-Admin-TOTP"
        ));
        configuration.setExposedHeaders(List.of("X-Request-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeSecurityError(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                request.getRequestURI()
        ));
    }
}
