package com.moviebooking.auth.security;

import com.moviebooking.auth.Role;
import com.moviebooking.auth.User;
import com.moviebooking.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiry;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expiry = Duration.ofMinutes(properties.security().jwtExpiryMinutes());
        this.clock = clock;
    }

    public String issueToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the principal encoded in a token, or empty for any invalid,
     * expired or tampered token. Never throws.
     */
    public Optional<UserPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new UserPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Duration expiry() {
        return expiry;
    }
}
