package com.moviebooking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.auth.security.JwtAuthFilter;
import com.moviebooking.common.error.ApiErrorResponse;
import com.moviebooking.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Open: auth, API docs, H2 console
                        .requestMatchers("/api/v1/auth/**", "/h2-console/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Open browsing: anyone can explore the catalog without an account
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/cities/**", "/api/v1/theaters/**",
                                "/api/v1/movies/**", "/api/v1/shows/**").permitAll()
                        // Admin surface
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Everything else (holds, bookings, notifications) needs a login
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                                        "Authentication required", request.getRequestURI()))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN,
                                        "You do not have permission to perform this action", request.getRequestURI())))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, ErrorCode code, String message, String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiErrorResponse.of(status, code, message, List.of(), path));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
