package com.settlr.settlr_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new LinkedHashMap<>();

        // ── PostgreSQL ping ──────────────────────────────────────────────────
        try (Connection conn = dataSource.getConnection()) {
            conn.isValid(1);
            status.put("postgres", "ok");
        } catch (Exception e) {
            status.put("postgres", "error: " + e.getMessage());
        }

        // ── Redis ping ───────────────────────────────────────────────────────
        try {
            String pong = Objects.requireNonNull(
                    redisTemplate.getConnectionFactory(),
                    "Redis connection factory is not configured"
            ).getConnection().ping();
            status.put("redis", "PONG".equalsIgnoreCase(pong) ? "ok" : "unexpected: " + pong);
        } catch (Exception e) {
            status.put("redis", "error: " + e.getMessage());
        }

        return ResponseEntity.ok(status);
    }
}
