package com.settlr.settlr_api.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlr.settlr_api.exception.GroqException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final GroqService groqService;
    private final InsightDataService insightDataService;
    private final ObjectMapper objectMapper;

    // ── Records ──────────────────────────────────────────────────────────────

    public record InsightCard(
            String type,     // "spending", "settlement", "pattern", "alert"
            String emoji,    // single emoji representing the insight
            String heading,  // short 4-6 word heading
            String body,     // 2-3 sentence insight text
            String metric,   // optional highlighted number e.g. "₹8,400"
            String trend     // "up", "down", "neutral"
    ) {}

    private static final String SYSTEM_PROMPT = """
            You are a personal finance insight engine for a bill-splitting app.
            Analyze the user's expense data and return ONLY a JSON array of
            exactly 4 insight objects. Each object must have these exact fields:
            type, emoji, heading, body, metric, trend.
            Types must be one of: spending, settlement, pattern, alert.
            trend must be one of: up, down, neutral.
            metric can be null if not applicable.
            Be specific with numbers. Use the user's actual data.
            No markdown. No explanation. Pure JSON array only.""";

    // ── Public API ───────────────────────────────────────────────────────────

    @Cacheable(value = "user-insights", key = "#userId")
    public List<InsightCard> generateInsights(UUID userId) {
        log.info("[INSIGHTS] Generating AI insights | userId={}", userId);

        // 1. Build context from real data
        InsightDataService.UserExpenseContext context =
                insightDataService.buildContext(userId, 30);

        // 2. Serialize context to JSON for the prompt
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new GroqException("Failed to serialize expense context", e);
        }

        // 3. Call Groq
        String userPrompt = "Here is the expense data: " + contextJson;
        String rawResponse = groqService.chat(SYSTEM_PROMPT, userPrompt);

        // 4. Parse response — strip markdown backticks if present
        String jsonContent = rawResponse.trim();
        if (jsonContent.startsWith("```json")) {
            jsonContent = jsonContent.substring(7);
        } else if (jsonContent.startsWith("```")) {
            jsonContent = jsonContent.substring(3);
        }
        if (jsonContent.endsWith("```")) {
            jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
        }
        jsonContent = jsonContent.trim();

        List<InsightCard> insights;
        try {
            insights = objectMapper.readValue(jsonContent, new TypeReference<List<InsightCard>>() {});
        } catch (Exception e) {
            log.error("[INSIGHTS] Failed to parse Groq response. Raw: {}", rawResponse);
            throw new GroqException("Failed to parse AI insights. Raw response: " + rawResponse, e);
        }

        // 5. Validate exactly 4 insight cards
        if (insights == null || insights.size() != 4) {
            throw new GroqException(
                    "Expected exactly 4 insight cards from Groq, got " +
                    (insights == null ? 0 : insights.size()) +
                    ". Raw response: " + rawResponse);
        }

        log.info("[INSIGHTS] Generated {} insight cards | userId={}", insights.size(), userId);
        return insights;
    }

    @CacheEvict(value = "user-insights", key = "#userId")
    public void evictInsights(UUID userId) {
        log.info("[INSIGHTS] Evicting cached insights | userId={}", userId);
    }
}
