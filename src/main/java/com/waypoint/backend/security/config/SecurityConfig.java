package com.waypoint.backend.security.config;

import com.waypoint.backend.config.application.CorsProperties;
import com.waypoint.backend.model.common.ApiErrorResponse;
import com.waypoint.backend.security.admin.AdminTotpFilter;
import com.waypoint.backend.security.jwt.JwtAuthenticationFilter;
import com.waypoint.backend.security.ratelimit.DistributedRateLimiter;
import com.waypoint.backend.security.ratelimit.RequestRateLimitFilter;
import com.waypoint.backend.service.admin.AdminAccountService;

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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder adminPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            AdminAccountService adminAccountService,
            DistributedRateLimiter distributedRateLimiter,
            Environment environment
    ) throws Exception {
        http
                .securityMatcher("/api/v1/admin/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers(request ->
                        environment.acceptsProfiles(Profiles.of("test"))
                                || request.getHeader("X-Admin-TOTP") != null))
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(adminAccountService)
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, authException) ->
                        writeSecurityError(
                                objectMapper,
                                request,
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Invalid admin credentials"
                        )))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/admin/accounts/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        .anyRequest().hasAnyRole("ADMIN", "SUPER_ADMIN"))
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
            http.addFilterAfter(new AdminTotpFilter(adminAccountService), BasicAuthenticationFilter.class);
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
            configuration.setAllowedOriginPatterns(List.of("chrome-extension://*"));
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
