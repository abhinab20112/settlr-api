package com.settlr.settlr_api.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlr.settlr_api.exception.GroqException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripSummaryService {

    private final GroqService groqService;
    private final InsightDataService insightDataService;
    private final ObjectMapper objectMapper;

    // ── Record ───────────────────────────────────────────────────────────────

    public record TripSummary(
            String title,             // e.g. "Goa Trip — October 2024"
            String narrative,         // 3-4 sentence story of the trip expenses
            List<String> highlights,  // 3 bullet point highlights
            String yourRole,          // personalised sentence about the requesting user
            String settlementAdvice,  // what they should do next
            BigDecimal totalSpend,
            String topCategory,
            int expenseCount
    ) {}

    // ── System Prompt ────────────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT = """
            You are a travel expense summariser for a group bill-splitting app. \
            Given group expense data, return ONLY a JSON object with these exact \
            fields: title, narrative, highlights (array of 3 strings), yourRole, \
            settlementAdvice, totalSpend, topCategory, expenseCount.
            Write in a friendly, conversational tone. Be specific with numbers. \
            highlights must be an array of exactly 3 strings.
            No markdown. Pure JSON only.""";

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Generates an AI-powered trip summary for a group.
     * Cached with key = groupId + requestingUserId, TTL = 2 hours.
     *
     * @param groupId          the group to summarise
     * @param requestingUserId the user viewing the summary (determines personalisation)
     * @return a fully populated {@link TripSummary}
     */
    @Cacheable(value = "trip-summaries", key = "#groupId + '_' + #requestingUserId")
    public TripSummary generateSummary(UUID groupId, UUID requestingUserId) {
        log.info("[TRIP-SUMMARY] Generating AI summary | groupId={} | userId={}", groupId, requestingUserId);

        // 1. Build rich context from real data (also validates membership)
        InsightDataService.GroupTripContext context =
                insightDataService.buildGroupContext(groupId, requestingUserId);

        // 2. Serialize context to JSON for the prompt
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new GroqException("Failed to serialize group context", e);
        }

        // 3. Call Groq
        String userPrompt = "Here is the group expense data: " + contextJson;
        TripSummary summary = groqService.chatAsJson(SYSTEM_PROMPT, userPrompt, TripSummary.class);

        // 4. Validate highlights count
        if (summary.highlights() == null || summary.highlights().size() != 3) {
            log.warn("[TRIP-SUMMARY] Groq returned {} highlights instead of 3, proceeding anyway",
                    summary.highlights() == null ? 0 : summary.highlights().size());
        }

        log.info("[TRIP-SUMMARY] Summary generated | group={} | title={}", context.groupName(), summary.title());
        return summary;
    }

    /**
     * Evicts cached trip summaries for a group.
     * Should be called when any new expense is added to the group.
     */
    @CacheEvict(value = "trip-summaries", allEntries = true, condition = "#groupId != null")
    public void evictSummariesForGroup(UUID groupId) {
        log.info("[TRIP-SUMMARY] Evicting cached summaries | groupId={}", groupId);
    }
}
