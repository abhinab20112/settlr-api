package com.settlr.settlr_api.security;

import com.settlr.settlr_api.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Stateless JWT utility — generates and parses tokens using jjwt 0.12.x API.
 * Claims stored in each token: sub (email), userId, email, iat, exp.
 */
@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String base64Secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // ── Key ──────────────────────────────────────────────────────────────────

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    }

    // ── Generate ─────────────────────────────────────────────────────────────

    /**
     * Builds a signed HS256 JWT containing userId and email as custom claims.
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        String token = Jwts.builder()
                .subject(user.getEmail())               // standard "sub" claim
                .claim("userId", user.getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())                 // HS256 by default
                .compact();

        log.debug("[JWT] Token generated | userId={} | expiresAt={}", user.getId(), expiry);
        return token;
    }

    // ── Parse ────────────────────────────────────────────────────────────────

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).get("userId", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);   // throws if expired or tampered
            return true;
        } catch (Exception ex) {
            log.warn("[JWT] Invalid token | reason={}", ex.getMessage());
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /**
     * Calculates the remaining time-to-live of a token in milliseconds.
     * Used during logout to set the Redis blacklist TTL.
     *
     * @param token the raw JWT string
     * @return remaining TTL in ms, or 0 if already expired
     */
    public long getRemainingTtlMs(String token) {
        try {
            Date expiration = extractAllClaims(token).getExpiration();
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0);
        } catch (Exception ex) {
            return 0;   // expired or invalid — nothing to blacklist
        }
    }
}
