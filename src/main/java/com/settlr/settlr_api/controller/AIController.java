package com.settlr.settlr_api.controller;

import com.settlr.settlr_api.entity.User;
import com.settlr.settlr_api.exception.GroqException;
import com.settlr.settlr_api.exception.ResourceNotFoundException;
import com.settlr.settlr_api.repository.UserRepository;
import com.settlr.settlr_api.service.ai.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users/me/insights")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AIController {

    private final InsightService insightService;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    private static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(10);

    /**
     * GET /api/users/me/insights
     *
     * Returns 4 AI-generated insight cards based on the user's
     * expense data from the last 30 days.
     * Returns 503 with a fallback flag if the AI service is unavailable.
     */
    @GetMapping
    public ResponseEntity<?> getInsights(Authentication authentication) {
        User user = resolveUser(authentication);

        try {
            List<InsightService.InsightCard> insights =
                    insightService.generateInsights(user.getId());
            return ResponseEntity.ok(insights);
        } catch (GroqException e) {
            log.warn("[AI] Insight generation failed for user={}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "AI insights temporarily unavailable",
                            "fallback", true
                    ));
        }
    }

    /**
     * GET /api/users/me/insights/refresh
     *
     * Evicts cached insights and regenerates them.
     * Rate-limited to 1 call per user per 10 minutes via Redis.
     * Returns 429 if the user has already refreshed recently.
     */
    @GetMapping("/refresh")
    public ResponseEntity<?> refreshInsights(Authentication authentication) {
        User user = resolveUser(authentication);
        String rateLimitKey = "insight-refresh:" + user.getId();

        // ── Rate-limit check via Redis ───────────────────────────────────────
        Boolean alreadyRefreshed = redisTemplate.hasKey(rateLimitKey);
        if (Boolean.TRUE.equals(alreadyRefreshed)) {
            log.info("[AI] Refresh rate-limited | user={}", user.getEmail());
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "You can refresh insights once every 10 minutes",
                            "retryAfterMinutes", 10
                    ));
        }

        // ── Set the cooldown key ─────────────────────────────────────────────
        redisTemplate.opsForValue().set(rateLimitKey, "1", REFRESH_COOLDOWN);

        try {
            insightService.evictInsights(user.getId());
            List<InsightService.InsightCard> insights =
                    insightService.generateInsights(user.getId());
            return ResponseEntity.ok(insights);
        } catch (GroqException e) {
            log.warn("[AI] Refresh failed for user={}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "AI insights temporarily unavailable",
                            "fallback", true
                    ));
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private User resolveUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
