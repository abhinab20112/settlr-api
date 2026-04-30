package com.settlr.settlr_api.service.ai;

import com.settlr.settlr_api.entity.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final GroqService groqService;

    public record CategoryResponse(String category) {}

    @Cacheable("expense-categories")
    public Category suggestCategory(String expenseDescription) {
        String systemPrompt = "You are an expense categorisation assistant. Given an expense " +
                "description, return ONLY a valid JSON object with one field: " +
                "'category'. The value must be exactly one of these enum values: " +
                "FOOD_AND_DINING, TRANSPORT, ACCOMMODATION, ENTERTAINMENT, " +
                "GROCERIES, UTILITIES, SHOPPING, MEDICAL, TRAVEL, OTHER. " +
                "No explanation. No markdown. JSON only.";

        String userPrompt = "Expense description: " + expenseDescription;

        try {
            CategoryResponse response = groqService.chatAsJson(systemPrompt, userPrompt, CategoryResponse.class);
            if (response != null && response.category() != null) {
                return Category.valueOf(response.category().trim().toUpperCase());
            }
            return Category.OTHER;
        } catch (IllegalArgumentException e) {
            log.warn("Groq returned an invalid category enum value. Falling back to OTHER.");
            return Category.OTHER;
        } catch (Exception e) {
            log.error("Failed to suggest category from Groq for description '{}'. Falling back to OTHER.", expenseDescription, e);
            return Category.OTHER;
        }
    }
}
