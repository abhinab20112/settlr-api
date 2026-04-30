package com.settlr.settlr_api.controller;

import com.settlr.settlr_api.entity.Category;
import com.settlr.settlr_api.service.ai.ExpenseCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    public record SuggestCategoryRequest(
            @NotBlank(message = "Description is required") String description
    ) {}

    public record SuggestCategoryResponse(
            String category,
            String displayName,
            String emoji
    ) {}

    /**
     * POST /api/expenses/suggest-category
     *
     * Auto-categorises an expense based on its description using Groq AI.
     * Returns the enum name, display name, and emoji for frontend rendering.
     */
    @PostMapping("/suggest-category")
    public ResponseEntity<SuggestCategoryResponse> suggestCategory(
            @Valid @RequestBody SuggestCategoryRequest request) {

        Category category = expenseCategoryService.suggestCategory(request.description());

        return ResponseEntity.ok(new SuggestCategoryResponse(
                category.name(),
                category.getDisplayName(),
                category.getEmoji()
        ));
    }
}
