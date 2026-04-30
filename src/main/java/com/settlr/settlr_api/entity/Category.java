package com.settlr.settlr_api.entity;

import lombok.Getter;

@Getter
public enum Category {
    FOOD_AND_DINING("Food & Dining", "🍔"),
    TRANSPORT("Transport", "🚗"),
    ACCOMMODATION("Accommodation", "🏨"),
    ENTERTAINMENT("Entertainment", "🍿"),
    GROCERIES("Groceries", "🛒"),
    UTILITIES("Utilities", "💡"),
    SHOPPING("Shopping", "🛍️"),
    MEDICAL("Medical", "💊"),
    TRAVEL("Travel", "✈️"),
    OTHER("Other", "📝");

    private final String displayName;
    private final String emoji;

    Category(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }
}
